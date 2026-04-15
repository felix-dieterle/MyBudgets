package de.mybudgets.app.util

import de.mybudgets.app.data.model.Account
import de.mybudgets.app.data.model.Category
import de.mybudgets.app.data.model.Transaction
import de.mybudgets.app.data.model.TransactionType
import kotlin.math.max

data class VirtualAccountOverview(
    val accountName: String,
    val income: Double,
    val expenses: Double,
    val balance: Double
)

object DashboardInsights {
    private const val DAY_MS = 24 * 60 * 60 * 1000L

    fun buildVirtualOverview(accounts: List<Account>, transactions: List<Transaction>): List<VirtualAccountOverview> {
        val virtualById = accounts.filter { it.isVirtual }.associateBy { it.id }
        return virtualById.values.map { account ->
            val accountTx = transactions.filter { it.virtualAccountId == account.id }
            val income = accountTx.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
            val expenses = accountTx.filter { it.type != TransactionType.INCOME }.sumOf { it.amount }
            VirtualAccountOverview(account.name, income, expenses, income - expenses)
        }.sortedByDescending { it.balance }
    }

    fun buildTrendSummary(now: Long, transactions: List<Transaction>): String {
        val last30 = now - 30 * DAY_MS
        val prev30 = now - 60 * DAY_MS
        val currentExpense = transactions.filter { it.date in last30..now && it.type != TransactionType.INCOME }.sumOf { it.amount }
        val previousExpense = transactions.filter { it.date in prev30 until last30 && it.type != TransactionType.INCOME }.sumOf { it.amount }
        val delta = currentExpense - previousExpense
        return "30d Ausgaben: ${CurrencyFormatter.format(currentExpense)} (${CurrencyFormatter.format(delta)} vs. vorherige 30d)"
    }

    fun buildPredictionWarnings(
        now: Long,
        accounts: List<Account>,
        categories: List<Category>,
        transactions: List<Transaction>
    ): List<String> {
        val warnings = mutableListOf<String>()
        val last30Start = now - 30 * DAY_MS
        val last30 = transactions.filter { it.date in last30Start..now }
        val avgDailyExpense = last30.filter { it.type != TransactionType.INCOME }.sumOf { it.amount } / 30.0
        warnings += "Prognose gesamt (nächste 30 Tage): ${CurrencyFormatter.format(avgDailyExpense * 30)} Ausgaben"

        val categoryNames = categories.associateBy({ it.id }, { it.name })
        last30.filter { it.type != TransactionType.INCOME && it.categoryId != null }
            .groupBy { it.categoryId!! }
            .toList()
            .sortedByDescending { (_, tx) -> tx.sumOf { it.amount } }
            .take(3)
            .forEach { (categoryId, tx) ->
                warnings += "Kategorie ${categoryNames[categoryId] ?: "#$categoryId"}: ${CurrencyFormatter.format(tx.sumOf { it.amount })} in 30 Tagen"
            }

        val virtualById = accounts.filter { it.isVirtual }.associateBy { it.id }
        last30.filter { it.virtualAccountId != null && it.type != TransactionType.INCOME }
            .groupBy { it.virtualAccountId!! }
            .toList()
            .sortedByDescending { (_, tx) -> tx.sumOf { it.amount } }
            .take(3)
            .forEach { (virtualId, tx) ->
                val name = virtualById[virtualId]?.name ?: "Virtuell #$virtualId"
                warnings += "$name: ${CurrencyFormatter.format(tx.sumOf { it.amount })} Ausgaben in 30 Tagen"
            }

        val virtualAccounts = accounts.filter { it.isVirtual && it.targetAmount != null && it.targetDueDate != null }
        for (account in virtualAccounts) {
            val accTx = transactions.filter { it.virtualAccountId == account.id }
            val saved = accTx.sumOf { if (it.type == TransactionType.INCOME) it.amount else -it.amount }
            val target = account.targetAmount ?: continue
            val due = account.targetDueDate ?: continue
            val daysLeft = ((due - now) / DAY_MS).toInt()
            if (daysLeft <= 0 && saved < target) {
                warnings += "🔴 Ziel verpasst: ${account.name} (${CurrencyFormatter.format(saved)} von ${CurrencyFormatter.format(target)})"
                continue
            }
            val recentNet = accTx.filter { it.date in last30Start..now }
                .sumOf { if (it.type == TransactionType.INCOME) it.amount else -it.amount }
            val projected = saved + (recentNet / 30.0) * max(daysLeft, 0)
            if (daysLeft in 0..90 && projected < target) {
                warnings += "⚠ ${account.name}: Ziel bis ${daysLeft} Tagen voraussichtlich nicht erreichbar (${CurrencyFormatter.format(projected)} / ${CurrencyFormatter.format(target)})"
            }
        }
        return warnings
    }
}
