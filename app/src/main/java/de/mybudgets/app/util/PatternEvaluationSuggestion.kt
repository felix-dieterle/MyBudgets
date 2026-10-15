package de.mybudgets.app.util

import de.mybudgets.app.data.model.Transaction

data class EvaluationSuggestion(
    val importance: Int,
    val cuttability: Int
)

/**
 * Kontext fuer die Bewertung einer einzelnen Buchung.
 *
 * @param patternKey   Schluessel, unter dem die Bewertung gespeichert wird
 *                     (Muster-Beschreibung, sonst die Beschreibung der Buchung).
 * @param suggestion   Vorschlag aus dem erkannten Muster (null, wenn die Buchung
 *                     zu keinem Muster gehoert).
 * @param belongsToPattern true, wenn die Buchung Teil eines erkannten Musters ist.
 */
data class TransactionEvaluationContext(
    val patternKey: String,
    val suggestion: EvaluationSuggestion?,
    val belongsToPattern: Boolean
)

object PatternEvaluationSuggestion {
    const val MATCHER_VERSION = "v1"
    const val UNRATED = 0
    const val MAX_RATING = 5

    fun keyOf(keyword: String): String =
        keyword.trim().lowercase().replace(Regex("\\s+"), " ")

    fun suggest(transactionCount: Int, confidence: Double, intervalDays: Int): EvaluationSuggestion {
        val importance = (1 + transactionCount / 3).coerceIn(1, MAX_RATING)

        val base = when {
            intervalDays <= 10 -> 3
            intervalDays <= 35 -> 2
            else -> 1
        }
        val cuttability = (base + if (confidence >= 0.8) 1 else 0).coerceIn(1, MAX_RATING)

        return EvaluationSuggestion(importance, cuttability)
    }

    /**
     * Ermittelt den Bewertungs-Kontext fuer eine einzelne Buchung.
     *
     * Gehoert die Buchung zu einem erkannten wiederkehrenden Muster, wird der
     * Muster-Schluessel und der daraus abgeleitete Vorschlag verwendet - so
     * landet die Bewertung auf demselben Schluessel wie im Muster-Dialog.
     * Andernfalls wird die normalisierte Beschreibung als Schluessel genutzt.
     *
     * @param detector injizierbar fuer Tests; Standard ist der echte Detector.
     */
    fun contextForTransaction(
        allTransactions: List<Transaction>,
        transaction: Transaction,
        detector: (List<Transaction>) -> List<RecurringPatternDetector.RecurringPattern> =
            { txs -> RecurringPatternDetector.detectPatterns(txs) }
    ): TransactionEvaluationContext {
        val match = try {
            detector(allTransactions).firstOrNull { pattern ->
                pattern.transactions.any { it.id != 0L && it.id == transaction.id }
            }
        } catch (e: Exception) {
            null
        }

        return if (match != null) {
            TransactionEvaluationContext(
                patternKey = keyOf(match.suggestedDescription),
                suggestion = suggest(
                    match.transactions.size,
                    match.confidence,
                    match.detectedIntervalDays
                ),
                belongsToPattern = true
            )
        } else {
            TransactionEvaluationContext(
                patternKey = keyOf(transaction.description),
                suggestion = null,
                belongsToPattern = false
            )
        }
    }
}
