package de.mybudgets.app.util

import de.mybudgets.app.data.model.RecurrencePattern
import de.mybudgets.app.data.model.Transaction
import kotlin.math.abs

/**
 * Matches transactions against recurrence patterns.
 * 
 * A transaction matches if ALL non-null criteria match:
 * - keywords: ANY keyword found in description (OR logic, case-insensitive)
 * - targetIban: Exact match (extracted from description, fallback to remoteId)
 * - amount: Within min/max range (inclusive)
 */
object RecurrencePatternMatcher {
    
    /**
     * Checks if a transaction matches a recurrence pattern.
     */
    fun matches(tx: Transaction, pattern: RecurrencePattern): Boolean {
        // Keywords check (OR logic: any keyword matches)
        if (!pattern.keywords.isNullOrBlank()) {
            val keywords = pattern.keywords.split(",").map { it.trim().lowercase() }
            val descLower = tx.description.lowercase()
            val hasMatch = keywords.any { keyword -> keyword.isNotBlank() && descLower.contains(keyword) }
            if (!hasMatch) return false
        }
        
        // IBAN check
        if (!pattern.targetIban.isNullOrBlank()) {
            val txIban = extractIban(tx) ?: return false
            if (!txIban.equals(pattern.targetIban, ignoreCase = true)) return false
        }
        
        // Amount range check
        if (pattern.amountMin != null && abs(tx.amount) < pattern.amountMin) return false
        if (pattern.amountMax != null && abs(tx.amount) > pattern.amountMax) return false
        
        return true
    }
    
    /**
     * Finds all transactions matching a pattern.
     */
    fun findMatches(transactions: List<Transaction>, pattern: RecurrencePattern): List<Transaction> {
        return transactions.filter { matches(it, pattern) }
    }
    
    /**
     * Extracts IBAN from transaction description or remoteId.
     * 
     * Heuristic: Looks for "IBAN: XXX" or "DE##..." patterns.
     */
    private fun extractIban(tx: Transaction): String? {
        // Try remoteId first (some banks store IBAN here)
        tx.remoteId?.let { 
            if (it.matches(Regex("[A-Z]{2}\\d{2}[A-Z0-9]+", RegexOption.IGNORE_CASE))) {
                return it.uppercase()
            }
        }
        
        // Try description
        val ibanPattern = Regex("IBAN[:\\s]*([A-Z]{2}\\d{2}[A-Z0-9]+)", RegexOption.IGNORE_CASE)
        ibanPattern.find(tx.description)?.groupValues?.getOrNull(1)?.let {
            return it.uppercase()
        }
        
        // Try loose IBAN pattern (DE## followed by alphanumerics)
        val loosePattern = Regex("([A-Z]{2}\\d{2}[A-Z0-9]{12,})", RegexOption.IGNORE_CASE)
        loosePattern.find(tx.description)?.groupValues?.getOrNull(1)?.let {
            return it.uppercase()
        }
        
        return null
    }
}
