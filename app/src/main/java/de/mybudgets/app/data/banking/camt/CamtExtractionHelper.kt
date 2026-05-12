package de.mybudgets.app.data.banking.camt

import de.mybudgets.app.util.AppLogger
import org.kapott.hbci.GV_Result.GVRKUms
import org.kapott.hbci.exceptions.HBCI_Exception
import org.kapott.hbci.manager.HBCIUtilsInternal
import org.kapott.hbci.structures.Konto
import java.lang.reflect.Method

/**
 * Interceptor for hbci4java's CAMT extraction process.
 * 
 * This class provides a wrapper around GVKUmsAllCamt.extractResults() that:
 * 1. Tries to extract the raw CAMT XML before JAXB parsing
 * 2. Catches JAXB failures (SAXNotRecognizedException)
 * 3. Falls back to our custom parser
 * 4. Injects the parsed transactions back into the GVRKUms result
 * 
 * **Integration point:**
 * This should be called from FintsService after HBCI job execution,
 * when we detect a CAMT parsing error.
 */
object CamtExtractionHelper {
    
    private const val TAG = "CamtExtractionHelper"
    
    /**
     * Extracts transactions from CAMT XML stored in the job result properties.
     * 
     * This method is called as a fallback when hbci4java's JAXB parser fails.
     * It extracts the raw CAMT XML from the job response, parses it with our
     * custom parser, and returns the transactions.
     * 
     * @param jobResultProperties The HBCIJobImpl's result properties (contains raw CAMT XML)
     * @param targetAccount The target account for filtering (optional)
     * @return Parsed transactions, or empty list on failure
     */
    fun extractFromJobResult(
        jobResultProperties: Map<String, String>,
        targetAccount: Konto? = null
    ): List<GVRKUms.UmsLine> {
        
        AppLogger.i(TAG, "extractFromJobResult: Attempting custom CAMT extraction")
        AppLogger.d(TAG, "extractFromJobResult: Job result has ${jobResultProperties.size} properties")
        
        // Find the CAMT XML in the result properties
        // hbci4java stores it under keys like "GVKUmsAllCamt_<n>.booked"
        val camtXml = findCamtXml(jobResultProperties)
        
        if (camtXml == null) {
            AppLogger.w(TAG, "extractFromJobResult: No CAMT XML found in job result")
            return emptyList()
        }
        
        AppLogger.i(TAG, "extractFromJobResult: Found CAMT XML (${camtXml.length} bytes)")
        
        // Parse with our custom parser
        return try {
            HbciCamtPatcher.parseAndConvert(camtXml, targetAccount)
        } catch (e: Exception) {
            AppLogger.e(TAG, "extractFromJobResult: Custom parser failed", e)
            emptyList()
        }
    }
    
    /**
     * Finds CAMT XML in the job result properties.
     * 
     * hbci4java stores CAMT XML under keys matching patterns like:
     * - "GVKUmsAllCamt.booked"
     * - "GVKUmsAllCamt_1.booked"
     * - "KUmsAllCamt.booked"
     * - etc.
     */
    private fun findCamtXml(properties: Map<String, String>): String? {
        // Try common keys first
        val commonKeys = listOf(
            "booked",
            "camt",
            "camtbooked",
            "GVKUmsAllCamt.booked",
            "KUmsAllCamt.booked"
        )
        
        for (key in commonKeys) {
            val value = properties[key]
            if (value != null && value.contains("<")) {
                AppLogger.d(TAG, "findCamtXml: Found CAMT XML at key '$key'")
                return value
            }
        }
        
        // Search all keys for XML content
        for ((key, value) in properties) {
            if (value.contains("<BkToCstmrAcctRpt") || 
                value.contains("<Document") && value.contains("<Ntry>")) {
                AppLogger.d(TAG, "findCamtXml: Found CAMT XML at key '$key'")
                return value
            }
        }
        
        // Last resort: dump all keys for debugging
        AppLogger.w(TAG, "findCamtXml: No CAMT XML found. Available keys: ${properties.keys.joinToString(", ")}")
        
        // Log first 200 chars of each property to help identify the XML
        for ((key, value) in properties) {
            val preview = if (value.length > 200) value.substring(0, 200) + "..." else value
            AppLogger.d(TAG, "findCamtXml: $key = $preview")
        }
        
        return null
    }
    
    /**
     * Injects parsed transactions into an existing GVRKUms result object.
     * 
     * @param result The GVRKUms result to modify
     * @param transactions Parsed transactions to add
     */
    fun injectTransactions(result: GVRKUms, transactions: List<GVRKUms.UmsLine>) {
        if (transactions.isEmpty()) {
            AppLogger.w(TAG, "injectTransactions: No transactions to inject")
            return
        }
        
        AppLogger.i(TAG, "injectTransactions: Injecting ${transactions.size} transactions into GVRKUms")
        
        try {
            // GVRKUms stores transactions in a public field: flatData
            val flatDataField = GVRKUms::class.java.getDeclaredField("flatData")
            flatDataField.isAccessible = true
            
            @Suppress("UNCHECKED_CAST")
            val existingData = flatDataField.get(result) as? MutableList<GVRKUms.UmsLine>
            
            if (existingData != null) {
                existingData.addAll(transactions)
                AppLogger.i(TAG, "injectTransactions: ✓ Added ${transactions.size} transactions to existing flatData")
            } else {
                // Create new list
                flatDataField.set(result, transactions.toMutableList())
                AppLogger.i(TAG, "injectTransactions: ✓ Created new flatData with ${transactions.size} transactions")
            }
            
            // Also update betrag field (total amount)
            updateResultStats(result, transactions)
            
        } catch (e: Exception) {
            AppLogger.e(TAG, "injectTransactions: Failed to inject transactions", e)
        }
    }
    
    /**
     * Updates summary statistics in the GVRKUms result.
     */
    private fun updateResultStats(result: GVRKUms, transactions: List<GVRKUms.UmsLine>) {
        try {
            // Calculate total amount
            val totalAmount = transactions.sumOf { it.value?.doubleValue ?: 0.0 }
            
            // Try to update betrag field
            val betragField = result.javaClass.getDeclaredField("betrag")
            betragField.isAccessible = true
            betragField.set(result, org.kapott.hbci.structures.Value(totalAmount, "EUR"))
            
            AppLogger.d(TAG, "updateResultStats: Updated total amount to $totalAmount EUR")
        } catch (e: Exception) {
            AppLogger.w(TAG, "updateResultStats: Could not update result stats", e)
        }
    }
    
    /**
     * Extracts raw CAMT XML directly from the HBCI dialog response.
     * 
     * This is called BEFORE hbci4java tries to parse with JAXB,
     * by intercepting the dialog response data.
     * 
     * @param dialogResponse Raw HBCI dialog response data
     * @return Extracted CAMT XML, or null if not found
     */
    fun extractCamtFromDialogResponse(dialogResponse: String): String? {
        AppLogger.d(TAG, "extractCamtFromDialogResponse: Searching for CAMT XML in response (${dialogResponse.length} bytes)")
        
        // HBCI response format: Key=Value pairs, CAMT XML is Base64-encoded in some cases
        // or directly embedded in others
        
        // Try to find XML markers
        val xmlStartMarkers = listOf(
            "<?xml",
            "<Document",
            "<BkToCstmrAcctRpt"
        )
        
        for (marker in xmlStartMarkers) {
            val startIndex = dialogResponse.indexOf(marker)
            if (startIndex >= 0) {
                AppLogger.d(TAG, "extractCamtFromDialogResponse: Found XML marker '$marker' at position $startIndex")
                // Extract from marker to end or next segment
                val xmlCandidate = dialogResponse.substring(startIndex)
                
                // Try to find end of XML
                val endMarker = "</Document>"
                val endIndex = xmlCandidate.indexOf(endMarker)
                if (endIndex >= 0) {
                    val xml = xmlCandidate.substring(0, endIndex + endMarker.length)
                    AppLogger.i(TAG, "extractCamtFromDialogResponse: ✓ Extracted CAMT XML (${xml.length} bytes)")
                    return xml
                }
            }
        }
        
        AppLogger.w(TAG, "extractCamtFromDialogResponse: No CAMT XML found")
        return null
    }
}
