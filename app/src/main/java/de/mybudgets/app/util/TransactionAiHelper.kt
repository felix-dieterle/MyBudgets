package de.mybudgets.app.util

import de.mybudgets.app.data.model.Category
import de.mybudgets.app.data.model.Transaction
import de.mybudgets.app.data.model.TransactionType
import kotlin.math.abs
import kotlin.math.max

object TransactionAiHelper {

    private const val AMOUNT_EPSILON = 0.01
    private const val DAY_MS = 24 * 60 * 60 * 1000L

    fun suggestCategoryId(
        description: String,
        amount: Double,
        type: TransactionType,
        recentTransactions: List<Transaction>,
        patternCategories: List<Category>
    ): Long? {
        val normalized = normalize(description) ?: return null

        val byHistory = recentTransactions
            .asSequence()
            .filter { it.categoryId != null }
            .filter { it.type == type }
            .filter { normalize(it.description) == normalized }
            .groupBy { it.categoryId!! }
            .maxWithOrNull(
                compareBy<Map.Entry<Long, List<Transaction>>> { it.value.size }
                    .thenBy { it.value.maxOfOrNull { tx -> tx.date } ?: 0L }
            )
            ?.key

        if (byHistory != null) return byHistory

        return PatternMatcher.match(description, patternCategories)
    }

    fun inferRecurringIntervalDays(
        description: String,
        amount: Double,
        type: TransactionType,
        currentDate: Long,
        recentTransactions: List<Transaction>
    ): Int? {
        val normalized = normalize(description) ?: return null
        val dates = (recentTransactions
            .asSequence()
            .filter { it.type == type && abs(it.amount - amount) <= AMOUNT_EPSILON }
            .filter { normalize(it.description) == normalized }
            .map { it.date }
            .toList() + currentDate)
            .distinct()
            .sorted()

        if (dates.size < 3) return null

        val intervals = dates.zipWithNext { a, b -> ((b - a) / DAY_MS).toInt() }
            .filter { it > 0 }
        if (intervals.size < 2) return null

        val sortedIntervals = intervals.sorted()
        val median = sortedIntervals[sortedIntervals.size / 2]
        val tolerance = max(2, median / 5)
        val stableIntervals = intervals.count { abs(it - median) <= tolerance }

        if (stableIntervals < 2 || stableIntervals < (intervals.size + 1) / 2) return null
        return median
    }

    private fun normalize(description: String): String? {
        val normalized = description
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
        return normalized.ifBlank { null }
    }
}
