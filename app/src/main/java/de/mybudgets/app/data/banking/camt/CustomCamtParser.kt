package de.mybudgets.app.data.banking.camt

import de.mybudgets.app.util.AppLogger
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Custom CAMT.052 XML parser for Android.
 * 
 * This parser is necessary because:
 * - hbci4java uses JAXB for CAMT parsing
 * - JAXB requires SAXParserFactory features that Android doesn't support
 * - Android's SAXParserFactory doesn't support "secure-processing" feature
 * 
 * This implementation uses Android's XmlPullParser (available on all API levels),
 * which is lightweight and doesn't require JAXB/SAX features.
 * 
 * Supports CAMT.052 formats:
 * - camt.052.001.02
 * - camt.052.001.08 (most common)
 * 
 * Usage:
 * ```
 * val result = CustomCamtParser.parse(xmlString)
 * if (result.transactions.isNotEmpty()) {
 *     // Process transactions
 * }
 * ```
 */
object CustomCamtParser {
    
    private const val TAG = "CustomCamtParser"
    
    /**
     * Date formats used in CAMT XML.
     * ISO 8601: YYYY-MM-DD or YYYY-MM-DDThh:mm:ss
     */
    private val DATE_FORMATS = listOf(
        SimpleDateFormat("yyyy-MM-dd", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    )
    
    /**
     * Parses CAMT.052 XML string into structured transactions.
     * 
     * @param xml Raw CAMT.052 XML string from bank
     * @return Parsed result with transactions, or empty result on error
     */
    fun parse(xml: String): CamtParseResult {
        if (xml.isBlank()) {
            AppLogger.w(TAG, "parse: XML is blank")
            return CamtParseResult.empty(warnings = listOf("XML is blank"))
        }
        
        return try {
            AppLogger.d(TAG, "parse: Starting CAMT.052 parse (${xml.length} bytes)")
            val parser = createParser(xml)
            val result = parseDocument(parser)
            AppLogger.i(TAG, "parse: SUCCESS - ${result.transactions.size} transactions, ${result.warnings.size} warnings")
            result
        } catch (e: Exception) {
            AppLogger.e(TAG, "parse: FAILED", e)
            CamtParseResult.empty(warnings = listOf("Parse error: ${e.message}"))
        }
    }
    
    /**
     * Creates configured XmlPullParser instance.
     */
    private fun createParser(xml: String): XmlPullParser {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))
        return parser
    }
    
    /**
     * Parses the entire CAMT document.
     * Navigates to <Rpt> elements and extracts account info + transactions.
     */
    private fun parseDocument(parser: XmlPullParser): CamtParseResult {
        val transactions = mutableListOf<CamtTransaction>()
        val warnings = mutableListOf<String>()
        var accountIban: String? = null
        var accountCurrency: String? = null
        var reportId: String? = null
        
        // Navigate to root element (usually <Document> or <BkToCstmrAcctRpt>)
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                val tagName = parser.name.removeNamespacePrefix()
                
                when (tagName) {
                    "Rpt" -> {
                        // Parse single report (contains account info + entries)
                        val reportData = parseReport(parser)
                        transactions.addAll(reportData.transactions)
                        
                        // Take account info from first report
                        if (accountIban == null) {
                            accountIban = reportData.accountIban
                            accountCurrency = reportData.accountCurrency
                            reportId = reportData.reportId
                        }
                        
                        warnings.addAll(reportData.warnings)
                    }
                }
            }
            eventType = parser.next()
        }
        
        if (transactions.isEmpty()) {
            warnings.add("No transactions found in CAMT XML")
        }
        
        return CamtParseResult(
            transactions = transactions,
            accountIban = accountIban,
            accountCurrency = accountCurrency,
            reportId = reportId,
            warnings = warnings
        )
    }
    
    /**
     * Parses a single <Rpt> (Report) element.
     * Contains account identification and multiple <Ntry> entries.
     */
    private fun parseReport(parser: XmlPullParser): CamtParseResult {
        val transactions = mutableListOf<CamtTransaction>()
        val warnings = mutableListOf<String>()
        var accountIban: String? = null
        var accountCurrency: String? = null
        var reportId: String? = null
        var balance: Double? = null
        
        val depth = parser.depth
        var eventType = parser.eventType
        
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.END_TAG && parser.depth == depth) {
                break // End of <Rpt>
            }
            
            if (eventType == XmlPullParser.START_TAG) {
                val tagName = parser.name.removeNamespacePrefix()
                
                when (tagName) {
                    "Id" -> {
                        if (parser.depth == depth + 1) {
                            reportId = parser.nextTextSafe()
                        }
                    }
                    "Acct" -> {
                        val acctData = parseAccount(parser)
                        accountIban = acctData.first
                        accountCurrency = acctData.second
                    }
                    "Bal" -> {
                        val bal = parseBalance(parser)
                        if (bal != null) balance = bal
                    }
                    "Ntry" -> {
                        val tx = parseEntry(parser)
                        if (tx != null) {
                            transactions.add(tx)
                        } else {
                            warnings.add("Failed to parse <Ntry> at line ${parser.lineNumber}")
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        
        return CamtParseResult(
            transactions = transactions,
            accountIban = accountIban,
            accountCurrency = accountCurrency,
            reportId = reportId,
            balance = balance,
            warnings = warnings
        )
    }
    
    /**
     * Parses a <Bal> element and returns the closing booked balance (CLBD) if found.
     * CAMT.052 balance types: OPBD (opening), CLBD (closing booked), ITBD (interim).
     */
    private fun parseBalance(parser: XmlPullParser): Double? {
        var balanceType: String? = null
        var amount: Double? = null
        var sign: String? = null
        
        val depth = parser.depth
        var eventType = parser.eventType
        
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.END_TAG && parser.depth == depth) {
                break
            }
            if (eventType == XmlPullParser.START_TAG) {
                val tagName = parser.name.removeNamespacePrefix()
                when (tagName) {
                    "Cd" -> if (balanceType == null) balanceType = parser.nextTextSafe()
                    "Amt" -> {
                        val amtText = parser.nextTextSafe()
                        amount = amtText?.toDoubleOrNull()
                    }
                    "CdtDbtInd" -> sign = parser.nextTextSafe()
                }
            }
            eventType = parser.next()
        }
        
        return if (balanceType == "CLBD" && amount != null) {
            if (sign == "DBIT") -amount else amount
        } else null
    }
    
    /**
     * Parses <Acct> (Account) element to extract IBAN and currency.
     * Returns Pair(iban, currency).
     */
    private fun parseAccount(parser: XmlPullParser): Pair<String?, String?> {
        var iban: String? = null
        var currency: String? = null
        
        val depth = parser.depth
        var eventType = parser.eventType
        
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.END_TAG && parser.depth == depth) {
                break
            }
            
            if (eventType == XmlPullParser.START_TAG) {
                val tagName = parser.name.removeNamespacePrefix()
                
                when (tagName) {
                    "IBAN" -> iban = parser.nextTextSafe()
                    "Ccy" -> currency = parser.nextTextSafe()
                }
            }
            eventType = parser.next()
        }
        
        return Pair(iban, currency)
    }
    
    /**
     * Parses a single <Ntry> (Entry/Transaction) element.
     * Contains amount, dates, and <NtryDtls> with detailed info.
     */
    private fun parseEntry(parser: XmlPullParser): CamtTransaction? {
        var amount: BigDecimal? = null
        var currency: String? = null
        var creditDebitIndicator: String? = null // "CRDT" or "DBIT"
        var bookingDate: Date? = null
        var valueDate: Date? = null
        var entryReference: String? = null
        
        // Details from <NtryDtls>/<TxDtls>
        var usage: String? = null
        var otherPartyName: String? = null
        var otherPartyIban: String? = null
        var otherPartyBic: String? = null
        var customerReference: String? = null
        var mandateReference: String? = null
        var creditorId: String? = null
        var transactionCode: String? = null
        
        val depth = parser.depth
        var eventType = parser.eventType
        
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.END_TAG && parser.depth == depth) {
                break
            }
            
            if (eventType == XmlPullParser.START_TAG) {
                val tagName = parser.name.removeNamespacePrefix()
                
                when (tagName) {
                    "Amt" -> {
                        currency = parser.getAttributeValue(null, "Ccy")
                        val amtText = parser.nextTextSafe()
                        amount = amtText?.toBigDecimalOrNull()
                    }
                    "CdtDbtInd" -> creditDebitIndicator = parser.nextTextSafe()
                    "BookgDt" -> bookingDate = parseDate(parser)
                    "ValDt" -> valueDate = parseDate(parser)
                    "AcctSvcrRef" -> entryReference = parser.nextTextSafe()
                    "NtryDtls" -> {
                        val details = parseEntryDetails(parser)
                        usage = details["usage"]
                        otherPartyName = details["otherPartyName"]
                        otherPartyIban = details["otherPartyIban"]
                        otherPartyBic = details["otherPartyBic"]
                        customerReference = details["customerReference"]
                        mandateReference = details["mandateReference"]
                        creditorId = details["creditorId"]
                        transactionCode = details["transactionCode"]
                    }
                }
            }
            eventType = parser.next()
        }
        
        // Validate required fields
        if (amount == null) {
            AppLogger.w(TAG, "parseEntry: Missing <Amt>, skipping entry")
            return null
        }
        
        // Apply credit/debit indicator (CRDT = +, DBIT = -)
        val signedAmount = when (creditDebitIndicator) {
            "DBIT" -> amount.negate()
            else -> amount // Default to positive (CRDT)
        }
        
        return CamtTransaction(
            amount = signedAmount,
            currency = currency ?: "EUR",
            bookingDate = bookingDate,
            valueDate = valueDate,
            usage = usage,
            otherPartyName = otherPartyName,
            otherPartyIban = otherPartyIban,
            otherPartyBic = otherPartyBic,
            customerReference = customerReference,
            mandateReference = mandateReference,
            creditorId = creditorId,
            transactionCode = transactionCode,
            entryReference = entryReference
        )
    }
    
    /**
     * Parses <NtryDtls>/<TxDtls> to extract detailed transaction info.
     * Returns map of extracted fields.
     */
    private fun parseEntryDetails(parser: XmlPullParser): Map<String, String?> {
        val details = mutableMapOf<String, String?>()
        val usageLines = mutableListOf<String>()
        
        val depth = parser.depth
        var eventType = parser.eventType
        
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.END_TAG && parser.depth == depth) {
                break
            }
            
            if (eventType == XmlPullParser.START_TAG) {
                val tagName = parser.name.removeNamespacePrefix()
                
                when (tagName) {
                    "RmtInf" -> {
                        // Remittance information (usage/purpose)
                        val rmtText = parseRemittanceInfo(parser)
                        if (rmtText != null) usageLines.add(rmtText)
                    }
                    "RltdPties" -> {
                        // Related parties (debtor/creditor)
                        val parties = parseRelatedParties(parser, details["cdtDbtInd"])
                        details["otherPartyName"] = parties.first
                        details["otherPartyIban"] = parties.second
                    }
                    "RltdAgts" -> {
                        // Related agents (bank BIC)
                        val bic = parseRelatedAgents(parser)
                        if (bic != null) details["otherPartyBic"] = bic
                    }
                    "Refs" -> {
                        // References (KREF, MREF, CRED)
                        parseReferences(parser, details)
                    }
                    "CdtDbtInd" -> {
                        details["cdtDbtInd"] = parser.nextTextSafe()
                    }
                    "BkTxCd" -> {
                        // Bank transaction code
                        val txCode = parseBankTransactionCode(parser)
                        if (txCode != null) details["transactionCode"] = txCode
                    }
                }
            }
            eventType = parser.next()
        }
        
        // Combine usage lines
        if (usageLines.isNotEmpty()) {
            details["usage"] = usageLines.joinToString(" ")
        }
        
        return details
    }
    
    /**
     * Parses <RmtInf> (Remittance Information) for usage text.
     */
    private fun parseRemittanceInfo(parser: XmlPullParser): String? {
        val lines = mutableListOf<String>()
        
        val depth = parser.depth
        var eventType = parser.eventType
        
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.END_TAG && parser.depth == depth) {
                break
            }
            
            if (eventType == XmlPullParser.START_TAG) {
                val tagName = parser.name.removeNamespacePrefix()
                
                when (tagName) {
                    "Ustrd" -> {
                        // Unstructured remittance info (most common)
                        val text = parser.nextTextSafe()
                        if (text != null) lines.add(text)
                    }
                    "Strd" -> {
                        // Structured remittance info
                        val text = parseStructuredRemittance(parser)
                        if (text != null) lines.add(text)
                    }
                }
            }
            eventType = parser.next()
        }
        
        return if (lines.isNotEmpty()) lines.joinToString(" ") else null
    }
    
    /**
     * Parses structured remittance info (less common).
     */
    private fun parseStructuredRemittance(parser: XmlPullParser): String? {
        val depth = parser.depth
        var eventType = parser.eventType
        
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.END_TAG && parser.depth == depth) {
                break
            }
            
            if (eventType == XmlPullParser.START_TAG) {
                val tagName = parser.name.removeNamespacePrefix()
                if (tagName == "AddtlRmtInf") {
                    return parser.nextTextSafe()
                }
            }
            eventType = parser.next()
        }
        return null
    }
    
    /**
     * Parses <RltdPties> to extract debtor/creditor name and IBAN.
     * Returns Pair(name, iban).
     */
    private fun parseRelatedParties(parser: XmlPullParser, cdtDbtInd: String?): Pair<String?, String?> {
        var name: String? = null
        var iban: String? = null
        
        val depth = parser.depth
        var eventType = parser.eventType
        
        // Determine which party to extract (Dbtr for CRDT, Cdtr for DBIT)
        val targetParty = if (cdtDbtInd == "DBIT") "Cdtr" else "Dbtr"
        
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.END_TAG && parser.depth == depth) {
                break
            }
            
            if (eventType == XmlPullParser.START_TAG) {
                val tagName = parser.name.removeNamespacePrefix()
                
                if (tagName == targetParty) {
                    val partyData = parseParty(parser)
                    name = partyData.first
                }
                
                if (tagName == "${targetParty}Acct") {
                    iban = parsePartyAccount(parser)
                }
            }
            eventType = parser.next()
        }
        
        return Pair(name, iban)
    }
    
    /**
     * Parses <Dbtr> or <Cdtr> to extract name.
     */
    private fun parseParty(parser: XmlPullParser): Pair<String?, String?> {
        var name: String? = null
        
        val depth = parser.depth
        var eventType = parser.eventType
        
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.END_TAG && parser.depth == depth) {
                break
            }
            
            if (eventType == XmlPullParser.START_TAG) {
                val tagName = parser.name.removeNamespacePrefix()
                if (tagName == "Nm") {
                    name = parser.nextTextSafe()
                }
            }
            eventType = parser.next()
        }
        
        return Pair(name, null)
    }
    
    /**
     * Parses <DbtrAcct> or <CdtrAcct> to extract IBAN.
     */
    private fun parsePartyAccount(parser: XmlPullParser): String? {
        val depth = parser.depth
        var eventType = parser.eventType
        
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.END_TAG && parser.depth == depth) {
                break
            }
            
            if (eventType == XmlPullParser.START_TAG) {
                val tagName = parser.name.removeNamespacePrefix()
                if (tagName == "IBAN") {
                    return parser.nextTextSafe()
                }
            }
            eventType = parser.next()
        }
        return null
    }
    
    /**
     * Parses <RltdAgts> to extract BIC.
     */
    private fun parseRelatedAgents(parser: XmlPullParser): String? {
        val depth = parser.depth
        var eventType = parser.eventType
        
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.END_TAG && parser.depth == depth) {
                break
            }
            
            if (eventType == XmlPullParser.START_TAG) {
                val tagName = parser.name.removeNamespacePrefix()
                if (tagName == "BIC") {
                    return parser.nextTextSafe()
                }
            }
            eventType = parser.next()
        }
        return null
    }
    
    /**
     * Parses <Refs> to extract KREF, MREF, CRED.
     */
    private fun parseReferences(parser: XmlPullParser, details: MutableMap<String, String?>) {
        val depth = parser.depth
        var eventType = parser.eventType
        
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.END_TAG && parser.depth == depth) {
                break
            }
            
            if (eventType == XmlPullParser.START_TAG) {
                val tagName = parser.name.removeNamespacePrefix()
                
                when (tagName) {
                    "EndToEndId" -> details["customerReference"] = parser.nextTextSafe() // KREF
                    "MndtId" -> details["mandateReference"] = parser.nextTextSafe() // MREF
                    "CdtrRefInf" -> {
                        // Creditor reference
                        val credRef = parseCreditorReference(parser)
                        if (credRef != null) details["creditorId"] = credRef
                    }
                }
            }
            eventType = parser.next()
        }
    }
    
    /**
     * Parses <CdtrRefInf> to extract creditor ID.
     */
    private fun parseCreditorReference(parser: XmlPullParser): String? {
        val depth = parser.depth
        var eventType = parser.eventType
        
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.END_TAG && parser.depth == depth) {
                break
            }
            
            if (eventType == XmlPullParser.START_TAG) {
                val tagName = parser.name.removeNamespacePrefix()
                if (tagName == "Ref") {
                    return parser.nextTextSafe()
                }
            }
            eventType = parser.next()
        }
        return null
    }
    
    /**
     * Parses <BkTxCd> to extract transaction code.
     */
    private fun parseBankTransactionCode(parser: XmlPullParser): String? {
        val depth = parser.depth
        var eventType = parser.eventType
        var domain: String? = null
        var family: String? = null
        
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.END_TAG && parser.depth == depth) {
                break
            }
            
            if (eventType == XmlPullParser.START_TAG) {
                val tagName = parser.name.removeNamespacePrefix()
                
                when (tagName) {
                    "Cd" -> domain = parser.nextTextSafe()
                    "Fmly" -> {
                        family = parseBankTransactionFamily(parser)
                    }
                }
            }
            eventType = parser.next()
        }
        
        return when {
            domain != null && family != null -> "$domain-$family"
            domain != null -> domain
            family != null -> family
            else -> null
        }
    }
    
    /**
     * Parses <Fmly> inside <BkTxCd>.
     */
    private fun parseBankTransactionFamily(parser: XmlPullParser): String? {
        val depth = parser.depth
        var eventType = parser.eventType
        
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.END_TAG && parser.depth == depth) {
                break
            }
            
            if (eventType == XmlPullParser.START_TAG) {
                val tagName = parser.name.removeNamespacePrefix()
                if (tagName == "Cd") {
                    return parser.nextTextSafe()
                }
            }
            eventType = parser.next()
        }
        return null
    }
    
    /**
     * Parses date from <Dt> or <DtTm> element.
     */
    private fun parseDate(parser: XmlPullParser): Date? {
        val depth = parser.depth
        var eventType = parser.eventType
        
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.END_TAG && parser.depth == depth) {
                break
            }
            
            if (eventType == XmlPullParser.START_TAG) {
                val tagName = parser.name.removeNamespacePrefix()
                
                when (tagName) {
                    "Dt" -> {
                        val dateText = parser.nextTextSafe()
                        return dateText?.let { parseIsoDate(it) }
                    }
                    "DtTm" -> {
                        val dateTimeText = parser.nextTextSafe()
                        return dateTimeText?.let { parseIsoDate(it) }
                    }
                }
            }
            eventType = parser.next()
        }
        return null
    }
    
    /**
     * Parses ISO 8601 date string with multiple format attempts.
     */
    private fun parseIsoDate(dateText: String): Date? {
        for (format in DATE_FORMATS) {
            try {
                return format.parse(dateText)
            } catch (e: Exception) {
                // Try next format
            }
        }
        AppLogger.w(TAG, "parseIsoDate: Failed to parse date: $dateText")
        return null
    }
    
    // ─── Extension Functions ─────────────────────────────────────────────────────
    
    /**
     * Removes namespace prefix from tag name (e.g. "ns2:Amt" → "Amt").
     */
    private fun String.removeNamespacePrefix(): String {
        val colonIndex = this.indexOf(':')
        return if (colonIndex >= 0) this.substring(colonIndex + 1) else this
    }
    
    /**
     * Safe version of parser.nextText() that catches exceptions.
     */
    private fun XmlPullParser.nextTextSafe(): String? {
        return try {
            val text = this.nextText()
            if (text.isBlank()) null else text.trim()
        } catch (e: Exception) {
            null
        }
    }
}
