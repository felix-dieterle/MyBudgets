package de.mybudgets.app.util

import de.mybudgets.app.data.model.Category
import de.mybudgets.app.data.model.Transaction
import de.mybudgets.app.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransactionAiHelperTest {

    @Test
    fun `prefers learned category from matching history`() {
        val recent = listOf(
            Transaction(accountId = 1, amount = 49.99, description = "Netflix.com", type = TransactionType.EXPENSE, categoryId = 7),
            Transaction(accountId = 1, amount = 49.99, description = "NETFLIX COM", type = TransactionType.EXPENSE, categoryId = 7),
            Transaction(accountId = 1, amount = 49.99, description = "Netflix", type = TransactionType.EXPENSE, categoryId = 9)
        )

        val suggested = TransactionAiHelper.suggestCategoryId(
            description = "Netflix.com",
            amount = 49.99,
            type = TransactionType.EXPENSE,
            recentTransactions = recent,
            patternCategories = emptyList()
        )

        assertEquals(7L, suggested)
    }

    @Test
    fun `falls back to regex patterns when no learned match exists`() {
        val categories = listOf(
            Category(id = 3, name = "Lebensmittel", pattern = "rewe|edeka")
        )

        val suggested = TransactionAiHelper.suggestCategoryId(
            description = "REWE Markt",
            amount = 20.0,
            type = TransactionType.EXPENSE,
            recentTransactions = emptyList(),
            patternCategories = categories
        )

        assertEquals(3L, suggested)
    }

    @Test
    fun `infers recurring interval for stable cadence`() {
        val day = 24 * 60 * 60 * 1000L
        val now = 120 * day
        val recent = listOf(
            Transaction(accountId = 1, amount = 12.99, description = "Spotify", type = TransactionType.EXPENSE, date = now - 60 * day),
            Transaction(accountId = 1, amount = 12.99, description = "Spotify", type = TransactionType.EXPENSE, date = now - 30 * day)
        )

        val interval = TransactionAiHelper.inferRecurringIntervalDays(
            description = "Spotify",
            amount = 12.99,
            type = TransactionType.EXPENSE,
            currentDate = now,
            recentTransactions = recent
        )

        assertEquals(30, interval)
    }

    @Test
    fun `does not infer recurring when history is too short`() {
        val day = 24 * 60 * 60 * 1000L
        val now = 90 * day
        val recent = listOf(
            Transaction(accountId = 1, amount = 100.0, description = "Miete", type = TransactionType.EXPENSE, date = now - 30 * day)
        )

        val interval = TransactionAiHelper.inferRecurringIntervalDays(
            description = "Miete",
            amount = 100.0,
            type = TransactionType.EXPENSE,
            currentDate = now,
            recentTransactions = recent
        )

        assertNull(interval)
    }
}
