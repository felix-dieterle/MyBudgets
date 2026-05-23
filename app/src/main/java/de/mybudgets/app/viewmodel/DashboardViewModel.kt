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
    val categoryLabels: Map<Long, String>
)

data class MonthlyTrendPoint(
    val label: String, // e.g. "Jan", "Feb"
    val income: Float,
    val expense: Float
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

    val categoryChartData: StateFlow<CategoryChartData> = combine(
        transactions,
        categoryRepo.observeAll(),
        selectedTimeRange
    ) { txs, cats, range ->
        val cutoff = when (range) {
            TimeRange.LAST_MONTH -> cutoffMillis(1)
            TimeRange.LAST_3_MONTHS -> cutoffMillis(3)
            TimeRange.ALL -> 0L
        }
        val filtered = if (cutoff > 0L) txs.filter { it.date >= cutoff } else txs
        val expenses = filtered.filter { it.type == TransactionType.EXPENSE }
        val byCategory = expenses.groupBy { it.categoryId }
        val labels = cats.associate { it.id to it.name }
        val total = expenses.sumOf { it.amount }.toFloat().coerceAtLeast(1f)

        val entries = byCategory.entries
            .sortedByDescending { it.value.sumOf { t -> t.amount } }
            .map { (catId, txList) ->
                val sum = txList.sumOf { it.amount }.toFloat()
                val label = labels[catId] ?: "Sonstige"
                PieEntry(sum / total * 100f, label, catId?.toInt() ?: 0)
            }
        CategoryChartData(entries, labels)
    }.stateIn(viewModelScope, SharingStarted.Lazily, CategoryChartData(emptyList(), emptyMap()))

    val monthlyTrend: StateFlow<List<MonthlyTrendPoint>> = transactions
        .map { txs ->
            if (txs.isEmpty()) return@map emptyList()
            
            val grouped = groupByMonth(txs)
            val sorted = grouped.entries.sortedBy { parseMonthLabel(it.key) }
            if (sorted.isEmpty()) return@map emptyList()
            
            val firstMonthKey = parseMonthLabel(sorted.first().key)
            val lastMonthKey = parseMonthLabel(sorted.last().key)
            
            (firstMonthKey..lastMonthKey).map { monthKey ->
                val cal = Calendar.getInstance().apply {
                    val year = 2000 + monthKey / 12
                    val month = monthKey % 12
                    set(year, month, 1, 0, 0, 0)
                }
                val label = monthLabel(cal)
                val monthTxs = grouped[label] ?: emptyList()
                MonthlyTrendPoint(
                    label = label,
                    income = monthTxs.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }.toFloat(),
                    expense = monthTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }.toFloat()
                )
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

        val categoryLabels = cats.associate { it.id to it.name }
        
        // Calculate averages per category for last 3 months
        val recentMonths = sorted.takeLast(3).map { it.value }
        val categoryAverages = mutableMapOf<Long?, Float>()
        
        recentMonths.forEach { monthTxs ->
            monthTxs.filter { it.type == TransactionType.EXPENSE }
                .groupBy { it.categoryId }
                .forEach { (catId, catTxs) ->
                    val sum = catTxs.sumOf { it.amount }.toFloat()
                    categoryAverages[catId] = (categoryAverages[catId] ?: 0f) + (sum / recentMonths.size)
                }
        }
        
        // Calculate fixed costs (recurring expenses average)
        val fixedCostsAvg = recentMonths.map { monthTxs ->
            monthTxs.filter { it.type == TransactionType.EXPENSE && it.isRecurring }
                .sumOf { it.amount }.toFloat()
        }.average().toFloat()
        
        // Total expense average (legacy)
        val avgExpense = recentMonths.map { monthTxs ->
            monthTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }.toFloat()
        }.average().toFloat()
        
        val lastLabel = sorted.last().key

        (1..3).map { offset ->
            val nextCal = parseMonthToCalendar(lastLabel).apply { add(Calendar.MONTH, offset) }
            val label = monthLabel(nextCal)
            
            // Top 5 categories
            val topCategories = categoryAverages.entries
                .sortedByDescending { it.value }
                .take(5)
                .associate { (catId, avg) ->
                    val catName = categoryLabels[catId] ?: "Sonstige"
                    catName to avg
                }
            
            ForecastPoint(
                label = label,
                predicted = avgExpense.coerceAtLeast(0f),
                categoryForecasts = topCategories,
                fixedCosts = fixedCostsAvg
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
