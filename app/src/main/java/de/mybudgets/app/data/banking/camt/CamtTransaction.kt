package de.mybudgets.app.data.banking.camt

import java.math.BigDecimal
import java.util.Date

/**
 * Represents a single transaction entry from CAMT.052 XML (Account Report).
 * 
 * CAMT.052 Structure:
 * - BkToCstmrAcctRpt (root)
 *   - Rpt (one or more reports)
 *     - Ntry (one or more entries/transactions)
 *       - Amt (amount + currency)
 *       - CdtDbtInd (Credit/Debit indicator)
 *       - BookgDt (booking date)
 *       - ValDt (value date)
 *       - NtryDtls (entry details)
 *         - TxDtls (transaction details)
 *           - RmtInf (remittance info / usage)
 *           - RltdPties (related parties - debtor/creditor)
 *           - RltdAgts (related agents - bank info)
 */
data class CamtTransaction(
    /**
     * Transaction amount (positive for credit, negative for debit after applying CdtDbtInd).
     */
    val amount: BigDecimal,
    
    /**
     * Currency code (e.g. "EUR").
     */
    val currency: String,
    
    /**
     * Booking date (when transaction was booked by bank).
     */
    val bookingDate: Date?,
    
    /**
     * Value date (when amount becomes available/charged).
     */
    val valueDate: Date?,
    
    /**
     * Usage / Purpose / Remittance information (Verwendungszweck).
     */
    val usage: String?,
    
    /**
     * Other party name (counterparty - sender for credit, receiver for debit).
     */
    val otherPartyName: String?,
    
    /**
     * Other party IBAN.
     */
    val otherPartyIban: String?,
    
    /**
     * Other party BIC.
     */
    val otherPartyBic: String?,
    
    /**
     * Customer reference (KREF).
     */
    val customerReference: String?,
    
    /**
     * Mandate reference (MREF).
     */
    val mandateReference: String?,
    
    /**
     * Creditor ID (CRED).
     */
    val creditorId: String?,
    
    /**
     * Transaction type code (e.g. SEPA credit transfer, direct debit).
     */
    val transactionCode: String?,
    
    /**
     * Raw entry reference from bank (unique ID).
     */
    val entryReference: String?
) {
    companion object {
        /**
         * Placeholder for empty/missing transaction.
         */
        fun empty() = CamtTransaction(
            amount = BigDecimal.ZERO,
            currency = "EUR",
            bookingDate = null,
            valueDate = null,
            usage = null,
            otherPartyName = null,
            otherPartyIban = null,
            otherPartyBic = null,
            customerReference = null,
            mandateReference = null,
            creditorId = null,
            transactionCode = null,
            entryReference = null
        )
    }
}

/**
 * Result of parsing a CAMT.052 XML document.
 */
data class CamtParseResult(
    /**
     * List of parsed transactions.
     */
    val transactions: List<CamtTransaction>,
    
    /**
     * Account IBAN from the report.
     */
    val accountIban: String?,
    
    /**
    * Account currency from the report.
    */
    val accountCurrency: String?,
    
    /**
    * Report ID from the bank.
    */
    val reportId: String?,
    
    /**
    * Closing booked balance (CLBD) extracted from CAMT XML, if available.
    */
    val balance: Double? = null,
    
    /**
    * Warnings encountered during parsing (non-fatal).
    */
    val warnings: List<String> = emptyList()
) {
    companion object {
        /**
         * Empty result for failed parsing.
         */
        fun empty(warnings: List<String> = emptyList()) = CamtParseResult(
            transactions = emptyList(),
            accountIban = null,
            accountCurrency = null,
            reportId = null,
            warnings = warnings
        )
    }
}
