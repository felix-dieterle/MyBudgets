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
    private const val INTERVAL_TOLERANCE_RATIO = 0.25 // ±25% vom Durchschnittsintervall (war: absolute 3 Tage)
    private const val MIN_OCCURRENCES = 3 // Need at least 3 occurrences to detect pattern
    
    data class RecurringPattern(
        val transactions: List<Transaction>,
        val detectedIntervalDays: Int,
        val confidence: Double,
        val suggestedDescription: String,
        val intervalLabel: String = "",
        val reasoning: String = ""
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
        return try {
            AppLogger.i("RecurringPatternDetector", "detectPatterns: START with ${transactions.size} transactions")
            
            if (transactions.size < minOccurrences) {
                AppLogger.i("RecurringPatternDetector", "  → Zu wenig Transaktionen (${transactions.size} < $minOccurrences)")
                return emptyList()
            }
            
            // Group by similar amounts
            AppLogger.i("RecurringPatternDetector", "  → Gruppiere nach ähnlichen Beträgen...")
            val amountGroups = groupBySimilarAmount(transactions)
            AppLogger.i("RecurringPatternDetector", "  → ${amountGroups.size} Gruppen gefunden")
            
            val patterns = mutableListOf<RecurringPattern>()
            
            for ((groupIdx, group) in amountGroups.withIndex()) {
                try {
                    if (group.size < minOccurrences) continue
                    
                    AppLogger.i("RecurringPatternDetector", "  Gruppe $groupIdx: ${group.size} TX, Betrag ~${group.firstOrNull()?.amount}")
                    
                    // Sort by date
                    val sorted = group.sortedBy { it.date }
                    
                    // Calculate intervals between consecutive transactions
                    val intervals = mutableListOf<Long>()
                    for (i in 0 until sorted.size - 1) {
                        val daysDiff = (sorted[i + 1].date - sorted[i].date) / (1000 * 60 * 60 * 24)
                        intervals.add(daysDiff)
                    }
                    
                    if (intervals.isEmpty()) {
                        AppLogger.i("RecurringPatternDetector", "    → Keine Intervalle berechenbar, skip")
                        continue
                    }
                    
                    // Check if intervals are regular (relative tolerance)
                    val avgInterval = intervals.average()
                    val maxDeviation = intervals.maxOfOrNull { abs(it - avgInterval) } ?: Double.MAX_VALUE
                    val toleranceDays = avgInterval * INTERVAL_TOLERANCE_RATIO // 25% vom avg statt feste 3 Tage
                    
                    AppLogger.i("RecurringPatternDetector", "    avgInterval=$avgInterval, maxDeviation=$maxDeviation, tolerance=$toleranceDays")
                    
                    if (maxDeviation <= toleranceDays) {
                        // Check if amounts are exact or just similar
                        val amountExactness = calculateAmountExactness(sorted)
                        
                        val confidence = calculateConfidence(
                            occurrences = sorted.size,
                            intervalStability = 1.0 - (maxDeviation / avgInterval.coerceAtLeast(1.0)),
                            descriptionSimilarity = calculateDescriptionSimilarity(sorted),
                            amountExactness = amountExactness
                        )
                        
                        val intervalDays = avgInterval.toInt()
                        val label = intervalLabel(intervalDays)
                        val reason = buildReasoning(sorted, intervalDays, label, confidence)

                        val pattern = RecurringPattern(
                            transactions = sorted,
                            detectedIntervalDays = intervalDays,
                            confidence = confidence,
                            suggestedDescription = extractCommonDescription(sorted),
                            intervalLabel = label,
                            reasoning = reason
                        )
                        patterns.add(pattern)
                        AppLogger.i("RecurringPatternDetector", "    ✅ Pattern erkannt: ${pattern.suggestedDescription}, ${sorted.size} TX, Intervall=$intervalDays Tage, Konfidenz=${"%.2f".format(confidence)} (exactness=${"%.2f".format(amountExactness)})")
                    } else {
                        AppLogger.i("RecurringPatternDetector", "    → Intervalle nicht regelmäßig genug, skip")
                    }
                } catch (e: Exception) {
                    AppLogger.e("RecurringPatternDetector", "  ❌ Fehler bei Gruppe $groupIdx", e)
                }
            }
            
            val result = patterns.sortedByDescending { it.confidence }
            AppLogger.i("RecurringPatternDetector", "detectPatterns: DONE - ${result.size} Patterns gefunden")
            result
        } catch (e: Exception) {
            AppLogger.e("RecurringPatternDetector", "detectPatterns CRASH", e)
            emptyList()
        }
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
     * Calculates how exact the amounts in a group are.
     * 
     * @return 1.0 = All amounts are exactly the same (to the cent)
     *         0.5 = Amounts vary but within ±5%
     *         0.0 = Maximum deviation within tolerance
     */
    private fun calculateAmountExactness(transactions: List<Transaction>): Double {
        if (transactions.size < 2) return 1.0
        
        val amounts = transactions.map { it.amount }
        val firstAmount = amounts.first()
        
        // Check if all amounts are exactly the same
        if (amounts.all { abs(it - firstAmount) < 0.01 }) {
            return 1.0 // Exact match (cent precision)
        }
        
        // Calculate average deviation
        val avgAmount = amounts.average()
        val maxDeviation = amounts.maxOfOrNull { abs(it - avgAmount) } ?: 0.0
        val relativeDeviation = if (avgAmount != 0.0) maxDeviation / abs(avgAmount) else 0.0
        
        // Map relative deviation to exactness score
        // 0% deviation → 1.0
        // 5% deviation (tolerance limit) → 0.0
        return 1.0 - (relativeDeviation / AMOUNT_TOLERANCE).coerceIn(0.0, 1.0)
    }
    
    /**
     * Calculates confidence score (0.0 to 1.0) based on:
     * - Amount exactness (exact amounts = very high, similar amounts = very low)
     * - Number of occurrences (more = higher confidence)
     * - Interval stability (less deviation = higher confidence)
     * - Description similarity (more similar = higher confidence)
     * 
     * Priority: Exact amounts >> Occurrences > Interval stability > Description similarity
     */
    private fun calculateConfidence(
        occurrences: Int,
        intervalStability: Double,
        descriptionSimilarity: Double,
        amountExactness: Double = 0.5 // 1.0 = exact, 0.0 = only similar within ±5%
    ): Double {
        val occurrenceScore = minOf(occurrences / 10.0, 1.0) // Cap at 10 occurrences
        
        // Amount exactness is by far the most important factor
        // Exact amounts → 0.8-1.0 confidence
        // Similar amounts (±5%) → 0.1-0.3 confidence (very low!)
        val baseConfidence = if (amountExactness >= 0.99) {
            // Exact amounts: High confidence based on other factors
            occurrenceScore * 0.4 + intervalStability * 0.4 + descriptionSimilarity * 0.2
        } else {
            // Similar amounts: Very low confidence, mostly informational
            // Cap at 0.3 regardless of other factors
            val rawScore = occurrenceScore * 0.3 + intervalStability * 0.3 + descriptionSimilarity * 0.2
            minOf(rawScore, 0.3)
        }
        
        return baseConfidence
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

    private fun intervalLabel(days: Int): String = when {
        days % 365 == 0 -> "${days / 365}-jährlich"
        days % 30 == 0 && days / 30 in 2..3 -> "vierteljährlich"
        days % 30 == 0 -> "${days / 30}-monatlich"
        days % 7 == 0 -> "${days / 7}-wöchentlich"
        days == 1 -> "täglich"
        else -> "alle $days Tage"
    }

    private fun buildReasoning(
        txs: List<Transaction>,
        intervalDays: Int,
        label: String,
        confidence: Double
    ): String {
        val count = txs.size
        val firstDate = DateFormatter.formatDate(txs.first().date)
        val lastDate = DateFormatter.formatDate(txs.last().date)
        val range = "$firstDate – $lastDate"
        val intervalNote = if (intervalDays % 30 == 0) {
            val months = intervalDays / 30
            "im Abstand von $months Monat(en) ($label)"
        } else "alle $intervalDays Tage ($label)"
        val amtStr = CurrencyFormatter.format(txs.first().amount, "EUR")
        return "$count Buchungen von $firstDate bis $lastDate\n" +
            "Betrag: $amtStr · $intervalNote\n" +
            "Konfidenz: ${(confidence * 100).toInt()}%"
    }
}
