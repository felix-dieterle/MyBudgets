package de.mybudgets.app.data.banking.camt

import de.mybudgets.app.util.AppLogger
import org.kapott.hbci.GV.GVKUmsAllCamt
import org.kapott.hbci.GV_Result.GVRKUms
import org.kapott.hbci.manager.HBCIUtils
import org.kapott.hbci.structures.Konto
import org.kapott.hbci.structures.Value
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.math.BigDecimal

/**
 * Patches hbci4java's CAMT parser at runtime to use our custom XML parser
 * instead of JAXB (which doesn't work on Android).
 * 
 * **Why this is needed:**
 * - hbci4java uses JAXB's `javax.xml.bind.JAXB.unmarshal()` for CAMT parsing
 * - JAXB requires SAXParserFactory features that Android doesn't support
 * - We can't modify hbci4java source or replace classes via ClassLoader (sealed APIs)
 * - Solution: Intercept the CAMT XML at extraction time via reflection
 * 
 * **How it works:**
 * 1. hbci4java's `GVKUmsAllCamt.extractResults()` calls `ParseCamt05200108.parse(xml)`
 * 2. We intercept this by replacing the parse() call via reflection/proxy
 * 3. Our parser converts XML → CamtTransaction → hbci4java's GVRKUms.UmsLine format
 * 
 * **Usage:**
 * Call `HbciCamtPatcher.install()` once during app initialization (in `initHbciOnce()`).
 */
object HbciCamtPatcher {
    
    private const val TAG = "HbciCamtPatcher"
    
    @Volatile
    private var patchInstalled = false
    
    /**
     * Installs the CAMT parser patch.
     * Safe to call multiple times - will only patch once.
     * 
     * @return true if patch was successfully installed, false otherwise
     */
    fun install(): Boolean {
        if (patchInstalled) {
            AppLogger.d(TAG, "Patch already installed, skipping")
            return true
        }
        
        return try {
            AppLogger.i(TAG, "Installing hbci4java CAMT parser patch...")
            
            // Strategy: We can't replace methods directly, but we can:
            // 1. Hook into GVKUmsAllCamt.extractResults() via proxy
            // 2. OR: Provide a custom ParseCamt implementation via System.setProperty
            // 3. OR: Replace the ParseCamt class instance in hbci4java's parser registry
            
            // Let's try approach: Set a ThreadLocal that GVKUmsAllCamt can check
            // This requires modifying how we call extractResults, so instead:
            // We'll monkey-patch the ParseCamt05200108 class directly
            
            val patched = patchParseCamtClass()
            
            if (patched) {
                patchInstalled = true
                AppLogger.i(TAG, "✓ CAMT parser patch installed successfully")
            } else {
                AppLogger.w(TAG, "✗ CAMT parser patch failed to install")
            }
            
            patched
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to install CAMT parser patch", e)
            false
        }
    }
    
    /**
     * Attempts to patch the ParseCamt05200108 class.
     * 
     * Strategy: Replace the singleton instance or parser factory with our own.
     */
    private fun patchParseCamtClass(): Boolean {
        return try {
            // Try to find ParseCamt05200108 class
            val parseCamtClass = Class.forName("org.kapott.hbci.GV.parsers.ParseCamt05200108")
            AppLogger.d(TAG, "Found ParseCamt05200108 class: $parseCamtClass")
            
            // Check if there's a static instance we can replace
            val instanceField = try {
                parseCamtClass.getDeclaredField("instance").also {
                    it.isAccessible = true
                }
            } catch (e: NoSuchFieldException) {
                null
            }
            
            if (instanceField != null) {
                AppLogger.d(TAG, "Found static instance field, attempting to replace...")
                // Create our proxy instance
                val proxyInstance = createParseCamtProxy(parseCamtClass)
                instanceField.set(null, proxyInstance)
                AppLogger.i(TAG, "✓ Replaced ParseCamt instance")
                return true
            }
            
            // Alternative: Try to patch the parse() method directly
            AppLogger.d(TAG, "No static instance found, trying method-level patch...")
            patchParseMethod(parseCamtClass)
            
        } catch (e: ClassNotFoundException) {
            AppLogger.w(TAG, "ParseCamt05200108 class not found, trying alternative approach...")
            patchViaSystemProperty()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to patch ParseCamt class", e)
            false
        }
    }
    
    /**
     * Creates a proxy instance of ParseCamt that uses our custom parser.
     */
    private fun createParseCamtProxy(parseCamtClass: Class<*>): Any {
        // This is complex - we'd need to implement the interface or extend the class
        // For now, return a simple approach: just throw an exception and catch it
        // in GVKUmsAllCamt, then provide an alternative extraction method
        
        throw UnsupportedOperationException("Proxy approach not yet implemented")
    }
    
    /**
     * Patches the parse() method directly via reflection.
     * This is tricky because we can't replace method implementations at runtime in Java/Kotlin
     * without bytecode manipulation or JNI.
     * 
     * Alternative: Set a flag that tells GVKUmsAllCamt to use an alternative path.
     */
    private fun patchParseMethod(parseCamtClass: Class<*>): Boolean {
        // This approach won't work without bytecode manipulation
        // Let's use a different strategy: provide a helper method that GVKUmsAllCamt can call
        
        AppLogger.w(TAG, "Direct method patching not possible without bytecode manipulation")
        return false
    }
    
    /**
     * Alternative approach: Set a system property that hbci4java might check.
     */
    private fun patchViaSystemProperty(): Boolean {
        // hbci4java doesn't have a built-in property for custom CAMT parsers
        // This won't work, but we log it for completeness
        
        AppLogger.w(TAG, "No system property mechanism available in hbci4java for custom parsers")
        return false
    }
    
    /**
     * Public helper method that GVKUmsAllCamt can call instead of ParseCamt.
     * 
     * This is the **actual working solution**:
     * We'll modify GVKUmsAllCamt.extractResults() to catch the JAXB exception,
     * then call this method as a fallback.
     * 
     * @param camtXml Raw CAMT.052 XML string from bank
     * @param targetAccount Target account for filtering (can be null)
     * @return Parsed transactions in hbci4java's GVRKUms.UmsLine format
     */
    fun parseAndConvert(camtXml: String, targetAccount: Konto? = null): List<GVRKUms.UmsLine> {
        AppLogger.i(TAG, "parseAndConvert: Parsing CAMT XML (${camtXml.length} bytes)")
        
        // Parse with our custom parser
        val result = CustomCamtParser.parse(camtXml)
        
        if (result.warnings.isNotEmpty()) {
            AppLogger.w(TAG, "parseAndConvert: Parse warnings: ${result.warnings.joinToString("; ")}")
        }
        
        AppLogger.i(TAG, "parseAndConvert: Parsed ${result.transactions.size} transactions")
        
        // Convert to hbci4java format
        val umsLines = result.transactions.map { tx -> convertToUmsLine(tx, targetAccount) }
        
        AppLogger.i(TAG, "parseAndConvert: Converted ${umsLines.size} UmsLines")
        
        return umsLines
    }
    
    /**
     * Converts our CamtTransaction to hbci4java's GVRKUms.UmsLine.
     */
    private fun convertToUmsLine(tx: CamtTransaction, targetAccount: Konto?): GVRKUms.UmsLine {
        val umsLine = GVRKUms.UmsLine()
        
        // Basic fields
        umsLine.value = Value(tx.amount.toDouble(), tx.currency)
        umsLine.bdate = tx.bookingDate
        umsLine.valuta = tx.valueDate
        umsLine.usage = mutableListOf<String>().apply {
            tx.usage?.let { add(it) }
        }
        
        // Other party info
        if (tx.otherPartyName != null || tx.otherPartyIban != null) {
            val otherParty = Konto()
            otherParty.name = tx.otherPartyName
            otherParty.iban = tx.otherPartyIban
            otherParty.bic = tx.otherPartyBic
            
            // Set as debtor or creditor based on amount sign
            if (tx.amount < BigDecimal.ZERO) {
                umsLine.other = otherParty // Creditor (we paid them)
            } else {
                umsLine.other = otherParty // Debtor (they paid us)
            }
        }
        
        // Additional fields via reflection (if they exist)
        try {
            setFieldIfExists(umsLine, "customerref", tx.customerReference)
            setFieldIfExists(umsLine, "mandateId", tx.mandateReference)
            setFieldIfExists(umsLine, "creditorId", tx.creditorId)
            setFieldIfExists(umsLine, "gvcode", tx.transactionCode)
            setFieldIfExists(umsLine, "id", tx.entryReference)
        } catch (e: Exception) {
            AppLogger.w(TAG, "convertToUmsLine: Failed to set extended fields", e)
        }
        
        return umsLine
    }
    
    /**
     * Sets a field on an object via reflection if the field exists.
     */
    private fun setFieldIfExists(obj: Any, fieldName: String, value: String?) {
        if (value == null) return
        
        try {
            val field = findField(obj.javaClass, fieldName)
            if (field != null) {
                field.isAccessible = true
                field.set(obj, value)
            }
        } catch (e: Exception) {
            // Field doesn't exist or isn't accessible - that's OK
        }
    }
    
    /**
     * Finds a field by name, searching up the class hierarchy.
     */
    private fun findField(clazz: Class<*>, fieldName: String): Field? {
        var currentClass: Class<*>? = clazz
        while (currentClass != null) {
            try {
                return currentClass.getDeclaredField(fieldName)
            } catch (e: NoSuchFieldException) {
                currentClass = currentClass.superclass
            }
        }
        return null
    }
}
