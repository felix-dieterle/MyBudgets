package de.mybudgets.app.util

import de.mybudgets.app.data.model.Transaction
import kotlin.math.abs

/**
 * Detects recurring patterns in transactions based on amount similarity and temporal regularity.
 * 
 * Algorithm:
 * 1. Group transactions by similar amounts (±5% tolerance)
 * 2. For each group, analyze temporal intervals
 * 3. If intervals are regular (±3 days tolerance), mark as recurring pattern
 * 4. Return suggested recurring transactions with detected interval
 */
object RecurringPatternDetector {
    
    private const val AMOUNT_TOLERANCE = 0.05 // ±5%
    private const val INTERVAL_TOLERANCE_DAYS = 3 // ±3 days
    private const val MIN_OCCURRENCES = 3 // Need at least 3 occurrences to detect pattern
    
    data class RecurringPattern(
        val transactions: List<Transaction>,
        val detectedIntervalDays: Int,
        val confidence: Double, // 0.0 to 1.0
        val suggestedDescription: String
    )
    
    /**
     * Analyzes transactions and returns detected recurring patterns.
     * 
     * @param transactions List of transactions to analyze (should be sorted by date ascending)
     * @param minOccurrences Minimum number of occurrences to consider a pattern (default 3)
     * @return List of detected recurring patterns, sorted by confidence descending
     */
    fun detectPatterns(
        transactions: List<Transaction>,
        minOccurrences: Int = MIN_OCCURRENCES
    ): List<RecurringPattern> {
        if (transactions.size < minOccurrences) return emptyList()
        
        // Group by similar amounts
        val amountGroups = groupBySimilarAmount(transactions)
        
        val patterns = mutableListOf<RecurringPattern>()
        
        for (group in amountGroups) {
            if (group.size < minOccurrences) continue
            
            // Sort by date
            val sorted = group.sortedBy { it.date }
            
            // Calculate intervals between consecutive transactions
            val intervals = mutableListOf<Long>()
            for (i in 0 until sorted.size - 1) {
                val daysDiff = (sorted[i + 1].date - sorted[i].date) / (1000 * 60 * 60 * 24)
                intervals.add(daysDiff)
            }
            
            if (intervals.isEmpty()) continue
            
            // Check if intervals are regular
            val avgInterval = intervals.average()
            val maxDeviation = intervals.maxOfOrNull { abs(it - avgInterval) } ?: Double.MAX_VALUE
            
            if (maxDeviation <= INTERVAL_TOLERANCE_DAYS) {
                // Regular pattern detected!
                val confidence = calculateConfidence(
                    occurrences = sorted.size,
                    intervalStability = 1.0 - (maxDeviation / avgInterval),
                    descriptionSimilarity = calculateDescriptionSimilarity(sorted)
                )
                
                patterns.add(
                    RecurringPattern(
                        transactions = sorted,
                        detectedIntervalDays = avgInterval.toInt(),
                        confidence = confidence,
                        suggestedDescription = extractCommonDescription(sorted)
                    )
                )
            }
        }
        
        return patterns.sortedByDescending { it.confidence }
    }
    
    /**
     * Groups transactions by similar amounts (±5% tolerance).
     */
    private fun groupBySimilarAmount(transactions: List<Transaction>): List<List<Transaction>> {
        val groups = mutableListOf<MutableList<Transaction>>()
        val used = mutableSetOf<Int>()
        
        for (i in transactions.indices) {
            if (i in used) continue
            
            val group = mutableListOf(transactions[i])
            used.add(i)
            
            for (j in i + 1 until transactions.size) {
                if (j in used) continue
                
                if (isAmountSimilar(transactions[i].amount, transactions[j].amount)) {
                    group.add(transactions[j])
                    used.add(j)
                }
            }
            
            groups.add(group)
        }
        
        return groups
    }
    
    /**
     * Checks if two amounts are similar within tolerance (±5%).
     */
    private fun isAmountSimilar(amount1: Double, amount2: Double): Boolean {
        if (amount1 == 0.0 && amount2 == 0.0) return true
        val maxAmount = maxOf(abs(amount1), abs(amount2))
        val diff = abs(amount1 - amount2)
        return diff / maxAmount <= AMOUNT_TOLERANCE
    }
    
    /**
     * Calculates confidence score (0.0 to 1.0) based on:
     * - Number of occurrences (more = higher confidence)
     * - Interval stability (less deviation = higher confidence)
     * - Description similarity (more similar = higher confidence)
     */
    private fun calculateConfidence(
        occurrences: Int,
        intervalStability: Double,
        descriptionSimilarity: Double
    ): Double {
        val occurrenceScore = minOf(occurrences / 10.0, 1.0) // Cap at 10 occurrences
        return (occurrenceScore * 0.4 + intervalStability * 0.4 + descriptionSimilarity * 0.2)
    }
    
    /**
     * Calculates description similarity within a group (0.0 to 1.0).
     * Uses Levenshtein distance on normalized descriptions.
     */
    private fun calculateDescriptionSimilarity(transactions: List<Transaction>): Double {
        if (transactions.size < 2) return 1.0
        
        val descriptions = transactions.map { it.description.trim().lowercase() }
        val pairs = mutableListOf<Pair<String, String>>()
        
        for (i in descriptions.indices) {
            for (j in i + 1 until descriptions.size) {
                pairs.add(descriptions[i] to descriptions[j])
            }
        }
        
        val similarities = pairs.map { (a, b) ->
            val maxLen = maxOf(a.length, b.length)
            if (maxLen == 0) return@map 1.0
            val distance = levenshteinDistance(a, b)
            1.0 - (distance.toDouble() / maxLen)
        }
        
        return similarities.average()
    }
    
    /**
     * Extracts a common description from a group by finding the longest common substring.
     */
    private fun extractCommonDescription(transactions: List<Transaction>): String {
        if (transactions.isEmpty()) return ""
        if (transactions.size == 1) return transactions[0].description
        
        // Use first description as base, find common parts
        val base = transactions[0].description.trim()
        var common = base
        
        for (tx in transactions.drop(1)) {
            common = longestCommonSubstring(common, tx.description.trim())
        }
        
        return common.trim().ifBlank { transactions[0].description }
    }
    
    /**
     * Finds the longest common substring between two strings.
     */
    private fun longestCommonSubstring(s1: String, s2: String): String {
        val m = s1.length
        val n = s2.length
        val dp = Array(m + 1) { IntArray(n + 1) }
        var maxLen = 0
        var endIndex = 0
        
        for (i in 1..m) {
            for (j in 1..n) {
                if (s1[i - 1].equals(s2[j - 1], ignoreCase = true)) {
                    dp[i][j] = dp[i - 1][j - 1] + 1
                    if (dp[i][j] > maxLen) {
                        maxLen = dp[i][j]
                        endIndex = i
                    }
                }
            }
        }
        
        return if (maxLen > 0) s1.substring(endIndex - maxLen, endIndex) else ""
    }
    
    /**
     * Calculates Levenshtein distance between two strings.
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        val dp = Array(m + 1) { IntArray(n + 1) }
        
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j
        
        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (s1[i - 1].equals(s2[j - 1], ignoreCase = true)) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1,      // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }
        
        return dp[m][n]
    }
}
