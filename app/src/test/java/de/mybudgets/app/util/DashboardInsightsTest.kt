package de.mybudgets.app.util

import de.mybudgets.app.data.model.Account
import de.mybudgets.app.data.model.Category
import de.mybudgets.app.data.model.Transaction
import de.mybudgets.app.data.model.TransactionType
import org.junit.Assert.assertEquals
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

    @Test
    fun `builds virtual overview aggregates income expenses and balance`() {
        val accounts = listOf(
            Account(id = 10, name = "Urlaub", isVirtual = true),
            Account(id = 11, name = "Notgroschen", isVirtual = true),
            Account(id = 12, name = "Giro", isVirtual = false)
        )
        val tx = listOf(
            Transaction(accountId = 1, virtualAccountId = 10, amount = 500.0, type = TransactionType.INCOME),
            Transaction(accountId = 1, virtualAccountId = 10, amount = 120.0, type = TransactionType.EXPENSE),
            Transaction(accountId = 1, virtualAccountId = 11, amount = 50.0, type = TransactionType.EXPENSE)
        )

        val overview = DashboardInsights.buildVirtualOverview(accounts, tx).associateBy { it.accountName }

        assertEquals(500.0, overview["Urlaub"]?.income ?: 0.0, 0.001)
        assertEquals(120.0, overview["Urlaub"]?.expenses ?: 0.0, 0.001)
        assertEquals(380.0, overview["Urlaub"]?.balance ?: 0.0, 0.001)
        assertEquals(-50.0, overview["Notgroschen"]?.balance ?: 0.0, 0.001)
    }

    @Test
    fun `builds trend summary with current and previous 30d delta`() {
        val now = 2_000_000_000_000L
        val day = 24 * 60 * 60 * 1000L
        val tx = listOf(
            Transaction(accountId = 1, amount = 120.0, type = TransactionType.EXPENSE, date = now - 2 * day),
            Transaction(accountId = 1, amount = 40.0, type = TransactionType.EXPENSE, date = now - 10 * day),
            Transaction(accountId = 1, amount = 100.0, type = TransactionType.EXPENSE, date = now - 35 * day)
        )

        val summary = DashboardInsights.buildTrendSummary(now, tx)

        assertTrue(summary.contains("30d Ausgaben"))
        assertTrue(summary.contains("vs. vorherige 30d"))
    }
}
