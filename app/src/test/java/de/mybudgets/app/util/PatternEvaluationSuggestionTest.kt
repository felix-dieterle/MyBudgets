package de.mybudgets.app.util

import de.mybudgets.app.data.model.Transaction
import de.mybudgets.app.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternEvaluationSuggestionTest {

    @Test
    fun `keyOf lowercases, trims and collapses whitespace`() {
        assertEquals("miete aktuell", PatternEvaluationSuggestion.keyOf("  Miete   Aktuell "))
        assertEquals("steuernr 018", PatternEvaluationSuggestion.keyOf("STEUERNR\t018"))
    }

    @Test
    fun `importance scales with occurrence count and clamps to five`() {
        assertEquals(1, PatternEvaluationSuggestion.suggest(1, 0.5, 30).importance)
        assertEquals(2, PatternEvaluationSuggestion.suggest(3, 0.5, 30).importance)
        assertEquals(5, PatternEvaluationSuggestion.suggest(12, 0.5, 30).importance)
        assertEquals(5, PatternEvaluationSuggestion.suggest(99, 0.5, 30).importance)
    }

    @Test
    fun `cuttability rises for short intervals and high confidence`() {
        assertEquals(4, PatternEvaluationSuggestion.suggest(6, 0.9, 5).cuttability)
        assertEquals(3, PatternEvaluationSuggestion.suggest(6, 0.5, 5).cuttability)
        assertEquals(2, PatternEvaluationSuggestion.suggest(6, 0.5, 30).cuttability)
        assertEquals(2, PatternEvaluationSuggestion.suggest(6, 0.9, 90).cuttability)
    }

    @Test
    fun `matcher version is exposed for keying stored evaluations`() {
        assertEquals("v1", PatternEvaluationSuggestion.MATCHER_VERSION)
        assertEquals(0, PatternEvaluationSuggestion.UNRATED)
    }

    @Test
    fun `contextForTransaction uses pattern key and suggestion when tx belongs to a pattern`() {
        val tx = tx(id = 7L, description = "REWE SAGT DANKE 123")
        val pattern = RecurringPatternDetector.RecurringPattern(
            transactions = listOf(tx),
            detectedIntervalDays = 30,
            confidence = 0.9,
            suggestedDescription = "REWE"
        )

        val context = PatternEvaluationSuggestion.contextForTransaction(
            allTransactions = listOf(tx),
            transaction = tx,
            detector = { listOf(pattern) }
        )

        assertTrue(context.belongsToPattern)
        assertEquals("rewe", context.patternKey)
        assertNotNull(context.suggestion)
        assertEquals(1, context.suggestion!!.importance)
        assertEquals(3, context.suggestion!!.cuttability)
    }

    @Test
    fun `contextForTransaction falls back to description key without a pattern`() {
        val tx = tx(id = 3L, description = "  Bäcker   Meier ")

        val context = PatternEvaluationSuggestion.contextForTransaction(
            allTransactions = listOf(tx),
            transaction = tx,
            detector = { emptyList() }
        )

        assertFalse(context.belongsToPattern)
        assertEquals("bäcker meier", context.patternKey)
        assertNull(context.suggestion)
    }

    @Test
    fun `contextForTransaction survives a detector that throws`() {
        val tx = tx(id = 4L, description = "Tankstelle")

        val context = PatternEvaluationSuggestion.contextForTransaction(
            allTransactions = listOf(tx),
            transaction = tx,
            detector = { throw IllegalStateException("boom") }
        )

        assertFalse(context.belongsToPattern)
        assertEquals("tankstelle", context.patternKey)
        assertNull(context.suggestion)
    }

    private fun tx(id: Long, description: String): Transaction = Transaction(
        id = id,
        accountId = 1L,
        amount = -12.34,
        description = description,
        type = TransactionType.EXPENSE
    )
}

