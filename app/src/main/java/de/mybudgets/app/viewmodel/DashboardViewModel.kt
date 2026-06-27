package de.mybudgets.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mikephil.charting.data.PieEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import de.mybudgets.app.data.model.Transaction
import de.mybudgets.app.data.model.TransactionType
import de.mybudgets.app.data.repository.AccountRepository
import de.mybudgets.app.data.repository.CategoryRepository
import de.mybudgets.app.data.repository.TransactionRepository
import de.mybudgets.app.util.DashboardInsights
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import javax.inject.Inject

enum class TimeRange { LAST_MONTH, LAST_3_MONTHS, ALL }

data class CategoryChartData(
    val pieEntries: List<PieEntry>,
    val categoryLabels: Map<Long, String>,
    val categoryAmounts: Map<Long, Float> = emptyMap(),
    val levelCategoryIds: List<Long> = emptyList(),
    val drillDownParentName: String? = null
)

data class MonthlyTrendPoint(
    val label: String, // e.g. "Jan", "Feb"
    val income: Float,
    val expense: Float,
    val balance: Float = 0f,
    val categoryExpenses: Map<String, Float> = emptyMap()
)

private data class PieChartIntermediate(
    val entries: List<PieEntry>,
    val amounts: Map<Long, Float>,
    val levelIds: List<Long>,
    val drillParentName: String?
)

data class ForecastPoint(
    val label: String, // e.g. "Jun", "Jul", "Aug"
    val predicted: Float, // Total predicted expenses (legacy, kept for compatibility)
    val categoryForecasts: Map<String, Float> = emptyMap(), // Category name -> amount
    val fixedCosts: Float = 0f // Sum of all recurring/fixed expenses
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val accountRepo: AccountRepository,
    private val txRepo: TransactionRepository,
    private val categoryRepo: CategoryRepository
) : ViewModel() {

    val totalBalance = accountRepo.observeTotalBalance().stateIn(viewModelScope, SharingStarted.Lazily, 0.0)
    val accounts     = accountRepo.observeAll().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val transactions = txRepo.observeAll().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val recentTransactions = txRepo.observeAllWithCategory()
        .map { it.take(5) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val virtualOverview = combine(accounts, transactions) { accs, txs ->
        DashboardInsights.buildVirtualOverview(accs, txs)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val trendSummary = transactions
        .map { DashboardInsights.buildTrendSummary(System.currentTimeMillis(), it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, "")

    val predictionWarnings = combine(
        accounts,
        transactions,
        categoryRepo.observeAll()
    ) { accs, txs, categories ->
        DashboardInsights.buildPredictionWarnings(System.currentTimeMillis(), accs, categories, txs)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // ── Chart State ──

    val selectedTimeRange = MutableStateFlow(TimeRange.LAST_MONTH)
    val hiddenCategoryIds = MutableStateFlow<Set<Long>>(emptySet())
    val drillDownCategoryId = MutableStateFlow<Long?>(null)

    fun toggleHideCategory(id: Long) {
        hiddenCategoryIds.value = if (id in hiddenCategoryIds.value)
            hiddenCategoryIds.value - id else hiddenCategoryIds.value + id
    }
    fun drillDownCategory(id: Long?) { drillDownCategoryId.value = id }

    val categoryChartData: StateFlow<CategoryChartData> = combine(
        transactions,
        categoryRepo.observeAll(),
        selectedTimeRange,
        hiddenCategoryIds,
        drillDownCategoryId
    ) { txs, cats, range, hiddenIds, drillId ->
        val cutoff = when (range) {
            TimeRange.LAST_MONTH -> cutoffMillis(1)
            TimeRange.LAST_3_MONTHS -> cutoffMillis(3)
            TimeRange.ALL -> 0L
        }
        val filtered = if (cutoff > 0L) txs.filter { it.date >= cutoff } else txs
        val expenses = filtered.filter { it.type == TransactionType.EXPENSE }
        
        val categoryMap = cats.associateBy { it.id }
        val labels = cats.associate { it.id to it.name }
        
        fun getRootCategory(catId: Long?): Long? {
            if (catId == null) return null
            var current = categoryMap[catId] ?: return catId
            while (current.parentCategoryId != null) {
                current = categoryMap[current.parentCategoryId] ?: return catId
            }
            return current.id
        }
        
        val allExpenseCatIds = expenses.mapNotNull { it.categoryId }.toSet()
        fun hasChildrenInData(catId: Long): Boolean =
            cats.any { it.parentCategoryId == catId && it.id in allExpenseCatIds }
        
        val l1RootIds = cats.filter { it.parentCategoryId == null || it.level == 1 }
            .map { it.id }
        
        val pieResult = if (drillId == null) {
            // L1: all root categories (including hidden/zero), compute amounts for all
            val byRoot = expenses.groupBy { getRootCategory(it.categoryId) }
            val total = byRoot.values.sumOf { it.sumOf { t -> t.amount } }.toFloat().coerceAtLeast(1f)
            val allAmounts = l1RootIds.associateWith { cId ->
                byRoot[cId]?.sumOf { it.amount }?.toFloat() ?: 0f
            }
            val visible = allAmounts.filterKeys { it !in hiddenIds && allAmounts[it]!! > 0f }
            val ent = visible.entries
                .sortedByDescending { it.value }
                .map { (cId, sum) ->
                    PieEntry(sum / total * 100f, labels[cId] ?: "Sonstige", cId.toInt())
                }
            PieChartIntermediate(ent, allAmounts, l1RootIds, null)
        } else {
            // Drill-down: group by direct child of drillId
            val childIds = cats.filter { it.parentCategoryId == drillId }.map { it.id }.toSet()
            fun getDirectChild(cId: Long): Long {
                var cur = categoryMap[cId] ?: return cId
                while (cur.parentCategoryId != null && cur.parentCategoryId != drillId) {
                    cur = categoryMap[cur.parentCategoryId] ?: return cId
                }
                return cur.id
            }
            val drillTxs = expenses.filter { tx ->
                val catId = tx.categoryId ?: return@filter false
                categoryMap[catId]?.let { c ->
                    c.id == drillId || c.parentCategoryId == drillId || getRootCategory(catId) == drillId
                } ?: false
            }
            val allLevelIds = (childIds + drillId).sorted()
            val byChild = drillTxs.groupBy { getDirectChild(it.categoryId!!) }
            val total = drillTxs.sumOf { it.amount }.toFloat().coerceAtLeast(1f)
            val allAmounts = allLevelIds.associateWith { cId ->
                byChild[cId]?.sumOf { it.amount }?.toFloat() ?: 0f
            }
            val visible = allAmounts.filterKeys { it !in hiddenIds && allAmounts[it]!! > 0f }
            val ent = visible.entries
                .sortedByDescending { it.value }
                .map { (cId, sum) ->
                    val label = if (cId == drillId) "Rest (${labels[cId] ?: ""})" else (labels[cId] ?: "Sonstige")
                    PieEntry(sum / total * 100f, label, cId.toInt())
                }
            val parentName = labels[drillId] ?: ""
            PieChartIntermediate(ent, allAmounts, allLevelIds, parentName)
        }
        CategoryChartData(pieResult.entries, labels, pieResult.amounts, pieResult.levelIds, pieResult.drillParentName)
    }.stateIn(viewModelScope, SharingStarted.Lazily, CategoryChartData(emptyList(), emptyMap()))

    val monthlyTrend: StateFlow<List<MonthlyTrendPoint>> = combine(
        transactions,
        totalBalance,
        categoryRepo.observeAll()
    ) { txs, totalBal, cats ->
        if (txs.isEmpty()) return@combine emptyList()
        
        val grouped = groupByMonth(txs)
        val sorted = grouped.entries.sortedBy { parseMonthLabel(it.key) }
        if (sorted.isEmpty()) return@combine emptyList()
        
        val firstMonthKey = parseMonthLabel(sorted.first().key)
        val lastMonthKey = parseMonthLabel(sorted.last().key)
        
        val categoryMap = cats.associateBy { it.id }
        val catLabels = cats.associate { it.id to it.name }
        fun getRootCategory(catId: Long?): Long? {
            if (catId == null) return null
            var current = categoryMap[catId] ?: return catId
            while (current.parentCategoryId != null) {
                current = categoryMap[current.parentCategoryId] ?: return catId
            }
            return current.id
        }
        
        val monthlyPoints = (firstMonthKey..lastMonthKey).map { monthKey ->
            val cal = Calendar.getInstance().apply {
                val year = 2000 + monthKey / 12
                val month = monthKey % 12
                set(year, month, 1, 0, 0, 0)
            }
            val label = monthLabel(cal)
            val monthTxs = grouped[label] ?: emptyList()
            val income = monthTxs.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }.toFloat()
            val expense = monthTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }.toFloat()
            val byRoot = monthTxs.filter { it.type == TransactionType.EXPENSE }
                .groupBy { getRootCategory(it.categoryId) }
                .mapValues { (_, txs) -> txs.sumOf { it.amount }.toFloat() }
            val categoryExpenses = byRoot.mapKeys { (id, _) -> catLabels[id] ?: "Sonstige" }
            MonthlyTrendPoint(
                label = label,
                income = income,
                expense = expense,
                categoryExpenses = categoryExpenses
            )
        }
        
        val totalNet = monthlyPoints.sumByDouble { (it.income - it.expense).toDouble() }.toFloat()
        val startBalance = (totalBal?.toFloat() ?: 0f) - totalNet
        var cumNet = 0f
        monthlyPoints.map { point ->
            cumNet += point.income - point.expense
            point.copy(balance = startBalance + cumNet)
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val forecast: StateFlow<List<ForecastPoint>> = combine(
        transactions,
        selectedTimeRange,
        categoryRepo.observeAll()
    ) { txs, _, cats ->
        val monthly = groupByMonth(txs)
        val sorted = monthly.entries.sortedBy { parseMonthLabel(it.key) }
        if (sorted.size < 3) return@combine emptyList()

        val categoryMap = cats.associateBy { it.id }
        val categoryLabels = cats.associate { it.id to it.name }
        
        // Get root category (walk up to level 1)
        fun getRootCategory(catId: Long?): Long? {
            if (catId == null) return null
            var current = categoryMap[catId] ?: return catId
            while (current.parentCategoryId != null) {
                current = categoryMap[current.parentCategoryId] ?: return catId
            }
            return current.id
        }
        
        // Use at least 6 months for trend analysis (fallback to 3 if not available)
        val historySize = if (sorted.size >= 6) 6 else 3
        val recentMonths = sorted.takeLast(historySize)
        
        // Calculate trends per ROOT category using linear regression
        val categoryTrends = mutableMapOf<Long?, Pair<Float, Float>>() // rootCatId -> (avg, trend)
        
        txs.filter { it.type == TransactionType.EXPENSE }
            .groupBy { getRootCategory(it.categoryId) }
            .forEach { (rootCatId, catTxs) ->
                val monthlyAmounts = recentMonths.map { (monthKey, monthTxs) ->
                    monthTxs.filter { getRootCategory(it.categoryId) == rootCatId && it.type == TransactionType.EXPENSE }
                        .sumOf { it.amount }.toFloat()
                }
                
                // Linear regression: y = avg + trend * x
                val avg = monthlyAmounts.average().toFloat()
                val trend = if (monthlyAmounts.size >= 2) {
                    val n = monthlyAmounts.size
                    val sumX = (0 until n).sum().toFloat()
                    val sumY = monthlyAmounts.sum()
                    val sumXY = monthlyAmounts.mapIndexed { i, y -> i * y }.sum()
                    val sumX2 = (0 until n).sumOf { it * it }.toFloat()
                    
                    // trend = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX^2)
                    val numerator = n * sumXY - sumX * sumY
                    val denominator = n * sumX2 - sumX * sumX
                    if (denominator != 0f) numerator / denominator else 0f
                } else 0f
                
                categoryTrends[rootCatId] = Pair(avg, trend)
            }
        
        // Fixed costs (recurring): use simple average (more stable)
        val fixedCostsAvg = recentMonths.map { (_, monthTxs) ->
            monthTxs.filter { it.type == TransactionType.EXPENSE && it.isRecurring }
                .sumOf { it.amount }.toFloat()
        }.average().toFloat()
        
        val lastLabel = sorted.last().key
        val lastMonthIdx = historySize - 1

        (1..3).map { offset ->
            val nextCal = parseMonthToCalendar(lastLabel).apply { add(Calendar.MONTH, offset) }
            val label = monthLabel(nextCal)
            
            // Apply trend to top ROOT categories
            val topCategories = categoryTrends.entries
                .sortedByDescending { it.value.first } // Sort by average
                .take(5)
                .associate { (rootCatId, pair) ->
                    val (avg, trend) = pair
                    val catName = categoryLabels[rootCatId] ?: "Sonstige"
                    
                    // Predict: avg + trend * (lastMonthIdx + offset)
                    val predicted = (avg + trend * (lastMonthIdx + offset)).coerceAtLeast(0f)
                    catName to predicted
                }
            
            // Total predicted (sum of all category trends)
            val totalPredicted = categoryTrends.values.sumOf { (avg, trend) ->
                (avg + trend * (lastMonthIdx + offset)).toDouble()
            }.toFloat().coerceAtLeast(0f)
            
            ForecastPoint(
                label = label,
                predicted = totalPredicted,
                categoryForecasts = topCategories,
                fixedCosts = fixedCostsAvg // Fixkosten bleiben stabil
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun selectTimeRange(range: TimeRange) { selectedTimeRange.value = range }

    // ── Helpers ──

    private fun cutoffMillis(monthsAgo: Int): Long {
        val cal = Calendar.getInstance().apply { add(Calendar.MONTH, -monthsAgo) }
        return cal.timeInMillis
    }

    private fun groupByMonth(txs: List<Transaction>): Map<String, List<Transaction>> =
        txs.groupBy { monthLabel(Calendar.getInstance().apply { timeInMillis = it.date }) }

    private fun monthLabel(cal: Calendar): String {
        val month = cal.get(Calendar.MONTH)
        val year = cal.get(Calendar.YEAR) % 100
        val names = arrayOf("Jan","Feb","Mär","Apr","Mai","Jun","Jul","Aug","Sep","Okt","Nov","Dez")
        return "${names[month]}'$year"
    }

    private fun parseMonthLabel(label: String): Int {
        val names = arrayOf("Jan","Feb","Mär","Apr","Mai","Jun","Jul","Aug","Sep","Okt","Nov","Dez")
        val parts = label.split("'")
        if (parts.size != 2) return 0
        val monthIdx = names.indexOf(parts[0])
        val year = parts[1].toIntOrNull() ?: 0
        return year * 12 + monthIdx
    }

    private fun parseMonthToCalendar(label: String): Calendar {
        val names = arrayOf("Jan","Feb","Mär","Apr","Mai","Jun","Jul","Aug","Sep","Okt","Nov","Dez")
        val parts = label.split("'")
        val monthIdx = names.indexOf(parts[0]).coerceAtLeast(0)
        val year = (parts.getOrNull(1)?.toIntOrNull() ?: 0) + 2000
        return Calendar.getInstance().apply { set(year, monthIdx, 1, 0, 0, 0) }
    }
}
