package de.mybudgets.app.util

import de.mybudgets.app.data.model.Account
import de.mybudgets.app.data.model.Category
import de.mybudgets.app.data.model.Transaction
import de.mybudgets.app.data.model.TransactionType
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardInsightsTest {

    @Test
    fun `builds red alert when virtual target missed`() {
        val now = 2_000_000_000_000L
        val account = Account(
            id = 10,
            name = "Urlaub",
            isVirtual = true,
            targetAmount = 2000.0,
            targetDueDate = now - 1
        )
        val tx = listOf(Transaction(accountId = 1, virtualAccountId = 10, amount = 500.0, type = TransactionType.INCOME))

        val warnings = DashboardInsights.buildPredictionWarnings(now, listOf(account), emptyList(), tx)

        assertTrue(warnings.any { it.contains("🔴 Ziel verpasst") })
    }

    @Test
    fun `builds category and overall prediction lines`() {
        val now = 2_000_000_000_000L
        val category = Category(id = 5, name = "Lebensmittel")
        val tx = listOf(
            Transaction(accountId = 1, amount = 50.0, type = TransactionType.EXPENSE, categoryId = 5, date = now - 1_000L),
            Transaction(accountId = 1, amount = 20.0, type = TransactionType.EXPENSE, date = now - 2_000L)
        )

        val warnings = DashboardInsights.buildPredictionWarnings(now, emptyList(), listOf(category), tx)

        assertTrue(warnings.any { it.contains("Prognose gesamt") })
        assertTrue(warnings.any { it.contains("Kategorie Lebensmittel") })
    }
}
