package de.mybudgets.app.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mikephil.charting.data.PieEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import de.mybudgets.app.data.model.Category
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject

enum class TimeRange { LAST_MONTH, LAST_3_MONTHS, ALL }

data class CategoryChartData(
    val pieEntries: List<PieEntry>,
    val categoryLabels: Map<Long, String>,
    val categoryAmounts: Map<Long, Float> = emptyMap(),
    val levelCategoryIds: List<Long> = emptyList(),
    val drillDownParentName: String? = null,
    val incomeTotal: Float = 0f,
    val expenseTotal: Float = 0f,
    val monthCount: Int = 1
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
    val categoryForecasts: Map<String, Float> = emptyMap(), // configId -> amount
    val fixedCosts: Float = 0f, // Sum of all recurring/fixed expenses
    val isHistorical: Boolean = false // true = actual data, false = projected
)

data class ForecastLineConfig(
    val id: String,
    val label: String,
    val categoryIds: Set<Long>,
    val colorIndex: Int = 0
)

data class DonutSliceConfig(
    val id: String,
    val label: String,
    val categoryIds: Set<Long>,
    val colorIndex: Int = 0
)

enum class DonutDisplayMode { CATEGORIES, CUSTOM_SETS }

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val accountRepo: AccountRepository,
    private val txRepo: TransactionRepository,
    private val categoryRepo: CategoryRepository,
    private val application: Application
) : ViewModel() {

    val totalBalance = accountRepo.observeTotalBalance().stateIn(viewModelScope, SharingStarted.Lazily, 0.0)
    val accounts     = accountRepo.observeAll().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val transactions = txRepo.observeAll().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val recentTransactions = txRepo.observeAllWithCategory()
        .map { it.take(5) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

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

    val allCategories = categoryRepo.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ── Chart State ──

    val selectedTimeRange = MutableStateFlow(TimeRange.LAST_MONTH)
    val hiddenCategoryIds = MutableStateFlow<Set<Long>>(emptySet())
    val drillDownCategoryId = MutableStateFlow<Long?>(null)

    val virtualOverview = combine(accounts, transactions, selectedTimeRange) { accs, txs, range ->
        val cutoff = when (range) {
            TimeRange.LAST_MONTH -> cutoffMillis(1)
            TimeRange.LAST_3_MONTHS -> cutoffMillis(3)
            TimeRange.ALL -> 0L
        }
        val filtered = if (cutoff > 0L) txs.filter { it.date >= cutoff } else txs
        DashboardInsights.buildVirtualOverview(accs, filtered)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun toggleHideCategory(id: Long) {
        hiddenCategoryIds.value = if (id in hiddenCategoryIds.value)
            hiddenCategoryIds.value - id else hiddenCategoryIds.value + id
    }
    fun drillDownCategory(id: Long?) { drillDownCategoryId.value = id }

    // ── Forecast Line Configs ──

    // ── Donut Custom Sets ──

    private val _donutSliceConfigs = MutableStateFlow<List<DonutSliceConfig>>(emptyList())
    val donutSliceConfigs: StateFlow<List<DonutSliceConfig>> = _donutSliceConfigs
    val donutDisplayMode = MutableStateFlow(DonutDisplayMode.CATEGORIES)
    val drillFromSetId = MutableStateFlow<String?>(null)

    fun setDonutDisplayMode(mode: DonutDisplayMode) { donutDisplayMode.value = mode }
    fun setDrillFromSet(id: String?) { drillFromSetId.value = id }

    fun saveDonutSliceConfig(config: DonutSliceConfig) {
        val updated = _donutSliceConfigs.value.map { if (it.id == config.id) config else it }
        _donutSliceConfigs.value = updated
        saveDonutToPrefs(updated)
    }

    fun removeDonutSliceConfig(id: String) {
        val updated = _donutSliceConfigs.value.filter { it.id != id }
        _donutSliceConfigs.value = updated
        saveDonutToPrefs(updated)
    }

    fun resetDonutConfigs() {
        _donutSliceConfigs.value = emptyList()
        donutDisplayMode.value = DonutDisplayMode.CATEGORIES
        drillFromSetId.value = null
        chartPrefs.edit().remove("donut_slices").apply()
    }

    private fun saveDonutToPrefs(configs: List<DonutSliceConfig>) {
        val arr = JSONArray()
        configs.forEach { config ->
            val catIdsArr = JSONArray()
            config.categoryIds.forEach { catIdsArr.put(it) }
            val obj = JSONObject().apply {
                put("id", config.id)
                put("label", config.label)
                put("categoryIds", catIdsArr.toString())
                put("colorIndex", config.colorIndex)
            }
            arr.put(obj)
        }
        chartPrefs.edit().putString("donut_slices", arr.toString()).apply()
    }

    private fun loadDonutFromPrefs() {
        val json = chartPrefs.getString("donut_slices", null) ?: return
        try {
            val arr = JSONArray(json)
            val configs = (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val catIdsStr = obj.optString("categoryIds", "[]")
                val catIdsArr = JSONArray(catIdsStr)
                DonutSliceConfig(
                    id = obj.getString("id"),
                    label = obj.getString("label"),
                    categoryIds = (0 until catIdsArr.length()).map { catIdsArr.getLong(it) }.toSet(),
                    colorIndex = obj.optInt("colorIndex", 0)
                )
            }
            _donutSliceConfigs.value = configs
        } catch (_: Exception) {}
    }

    private val chartPrefs: SharedPreferences =
        application.getSharedPreferences("chart_configs", Context.MODE_PRIVATE)

    private val _forecastLineConfigs = MutableStateFlow<List<ForecastLineConfig>>(emptyList())
    val forecastLineConfigs: StateFlow<List<ForecastLineConfig>> = _forecastLineConfigs

    init {
        loadConfigsFromPrefs()
        loadDonutFromPrefs()
    }

    private fun loadConfigsFromPrefs() {
        val json = chartPrefs.getString("forecast_lines", null) ?: return
        try {
            val arr = JSONArray(json)
            val configs = (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val catIdsStr = obj.optString("categoryIds", "[]")
                val catIdsArr = JSONArray(catIdsStr)
                ForecastLineConfig(
                    id = obj.getString("id"),
                    label = obj.getString("label"),
                    categoryIds = (0 until catIdsArr.length()).map { catIdsArr.getLong(it) }.toSet(),
                    colorIndex = obj.optInt("colorIndex", 0)
                )
            }
            _forecastLineConfigs.value = configs
        } catch (_: Exception) {}
    }

    private fun saveToPrefs(configs: List<ForecastLineConfig>) {
        val arr = JSONArray()
        configs.forEach { config ->
            val catIdsArr = JSONArray()
            config.categoryIds.forEach { catIdsArr.put(it) }
            val obj = JSONObject().apply {
                put("id", config.id)
                put("label", config.label)
                put("categoryIds", catIdsArr.toString())
                put("colorIndex", config.colorIndex)
            }
            arr.put(obj)
        }
        chartPrefs.edit().putString("forecast_lines", arr.toString()).apply()
    }

    fun resetForecastConfigs() {
        _forecastLineConfigs.value = emptyList()
        chartPrefs.edit().remove("forecast_lines").apply()
    }

    fun saveLineConfig(config: ForecastLineConfig) {
        val updated = _forecastLineConfigs.value.map { if (it.id == config.id) config else it }
        _forecastLineConfigs.value = updated
        saveToPrefs(updated)
    }

    fun removeLineConfig(id: String) {
        val updated = _forecastLineConfigs.value.filter { it.id != id }
        _forecastLineConfigs.value = updated
        saveToPrefs(updated)
    }

    fun initializeDefaultConfigs() {
        if (_forecastLineConfigs.value.isNotEmpty()) return
        viewModelScope.launch {
            val txs = txRepo.observeAll().first()
            val cats = categoryRepo.observeAll().first()
            val defaults = generateDefaultConfigs(txs, cats)
            if (defaults.isNotEmpty()) {
                _forecastLineConfigs.value = defaults
                saveToPrefs(defaults)
            }
        }
    }

    val categoryChartData: StateFlow<CategoryChartData> = combine(
        transactions,
        categoryRepo.observeAll(),
        selectedTimeRange,
        hiddenCategoryIds,
        drillDownCategoryId,
        _donutSliceConfigs,
        donutDisplayMode,
        drillFromSetId
    ) { flows: Array<*> ->
        @Suppress("UNCHECKED_CAST")
        val txs = flows[0] as List<Transaction>
        val cats = flows[1] as List<Category>
        val range = flows[2] as TimeRange
        val hiddenIds = flows[3] as Set<Long>
        val drillId = flows[4] as Long?
        val sliceConfigs = flows[5] as List<DonutSliceConfig>
        val displayMode = flows[6] as DonutDisplayMode
        val drillSetId = flows[7] as String?
        
        val cutoff = when (range) {
            TimeRange.LAST_MONTH -> cutoffMillis(1)
            TimeRange.LAST_3_MONTHS -> cutoffMillis(3)
            TimeRange.ALL -> 0L
        }
        val filtered = if (cutoff > 0L) txs.filter { it.date >= cutoff } else txs
        val expenses = filtered.filter { it.type == TransactionType.EXPENSE }

        val incomeTotal = filtered.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }.toFloat()
        val expenseTotal = expenses.sumOf { it.amount }.toFloat()
        val monthCount = when (range) {
            TimeRange.LAST_MONTH -> 1
            TimeRange.LAST_3_MONTHS -> 3
            TimeRange.ALL -> {
                if (filtered.isEmpty()) 1
                else {
                    val dates = filtered.map { it.date }
                    val months = ((System.currentTimeMillis() - dates.min()) / (30L * 24 * 60 * 60 * 1000)).toInt().coerceAtLeast(1)
                    months
                }
            }
        }

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
        
        fun descendantIds(catId: Long): Set<Long> =
            cats.filter { it.parentCategoryId == catId }.flatMap { descendantIds(it.id) + it.id }.toSet()
        
        val l1RootIds = cats.filter { it.parentCategoryId == null || it.level == 1 }.map { it.id }

        // ── Custom sets mode ──
        if (displayMode == DonutDisplayMode.CUSTOM_SETS && sliceConfigs.isNotEmpty()) {
            if (drillSetId != null) {
                // Drill from set: show direct children of all categories in the set (flat)
                val setConfig = sliceConfigs.find { it.id == drillSetId } ?: sliceConfigs.first()
                val allSetCatIds = setConfig.categoryIds.flatMap { cid -> descendantIds(cid) + cid }.toSet()
                val childCats = cats.filter { it.parentCategoryId in setConfig.categoryIds }
                val drillFromTxs = expenses.filter { tx ->
                    val cid = tx.categoryId ?: return@filter false
                    cid in allSetCatIds
                }
                val allChildIds = childCats.map { it.id }.sorted()
                if (allChildIds.isEmpty()) {
                    return@combine CategoryChartData(emptyList(), emptyMap())
                }
                val byChild = drillFromTxs.groupBy { tx ->
                    var cur = categoryMap[tx.categoryId] ?: return@groupBy tx.categoryId
                    while (cur.parentCategoryId != null && cur.parentCategoryId !in setConfig.categoryIds) {
                        cur = categoryMap[cur.parentCategoryId] ?: return@groupBy tx.categoryId
                    }
                    cur.id
                }
                val total = drillFromTxs.sumOf { it.amount }.toFloat().coerceAtLeast(1f)
                val allAmounts = allChildIds.associateWith { cId ->
                    byChild[cId]?.sumOf { it.amount }?.toFloat() ?: 0f
                }
                val visible = allAmounts.filterKeys { it !in hiddenIds && allAmounts[it]!! > 0f }
                val ent = visible.entries
                    .sortedByDescending { it.value }
                    .map { (cId, sum) -> PieEntry(sum / total * 100f, labels[cId] ?: "Sonstige", cId.toInt()) }
                return@combine CategoryChartData(ent, labels, allAmounts, allChildIds, setConfig.label, incomeTotal, expenseTotal, monthCount)
            }

            // Aggregate by slices
            val sliceAmounts = sliceConfigs.associate { config ->
                val ids = config.categoryIds.flatMap { cid -> descendantIds(cid) + cid }.toSet()
                val sum = expenses.filter { it.categoryId in ids }.sumOf { it.amount }.toFloat()
                config.id to sum
            }
            val total = sliceAmounts.values.sum().coerceAtLeast(1f)
            val visible = sliceAmounts.filter { it.value > 0f }
            val ent = visible.entries
                .map { (configId, sum) ->
                    val config = sliceConfigs.find { it.id == configId }
                    val label = config?.label ?: "Unbekannt"
                    PieEntry(sum / total * 100f, label, config?.id ?: configId)
                }
                .sortedByDescending { it.value }
            val sliceLabels = sliceConfigs.associate { it.id.hashCode().toLong() to it.label }
            val sliceAmountsMap = sliceConfigs.associate { it.id.hashCode().toLong() to (sliceAmounts[it.id] ?: 0f) }
            val sliceLevelIds = sliceConfigs.map { it.id.hashCode().toLong() }
            return@combine CategoryChartData(ent, sliceLabels, sliceAmountsMap, sliceLevelIds, null, incomeTotal, expenseTotal, monthCount)
        }
        
        // ── Category mode (current behavior) ──
        val allExpenseCatIds = expenses.mapNotNull { it.categoryId }.toSet()
        
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
        CategoryChartData(pieResult.entries, labels, pieResult.amounts, pieResult.levelIds, pieResult.drillParentName, incomeTotal, expenseTotal, monthCount)
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
        categoryRepo.observeAll(),
        forecastLineConfigs
    ) { txs, range, cats, configs ->
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
        
        // Time range determines how many months to use for trend analysis
        val historySize = when (range) {
            TimeRange.ALL -> sorted.size
            TimeRange.LAST_3_MONTHS -> sorted.size.coerceAtMost(12)
            TimeRange.LAST_MONTH -> sorted.size.coerceAtMost(6)
        }
        val recentMonths = sorted.takeLast(historySize)

        if (configs.isEmpty()) {
            // ── Legacy fallback: top 5 root categories (original behavior) ──
            val categoryTrends = mutableMapOf<Long?, Pair<Float, Float>>()
            
            txs.filter { it.type == TransactionType.EXPENSE }
                .groupBy { getRootCategory(it.categoryId) }
                .forEach { (rootCatId, _) ->
                    val monthlyAmounts = recentMonths.map { (_, monthTxs) ->
                        monthTxs.filter { getRootCategory(it.categoryId) == rootCatId && it.type == TransactionType.EXPENSE }
                            .sumOf { it.amount }.toFloat()
                    }
                    val avg = monthlyAmounts.average().toFloat()
                    val trend = if (monthlyAmounts.size >= 2) {
                        val n = monthlyAmounts.size
                        val sumX = (0 until n).sum().toFloat()
                        val sumY = monthlyAmounts.sum()
                        val sumXY = monthlyAmounts.mapIndexed { i, y -> i * y }.sum()
                        val sumX2 = (0 until n).sumOf { it * it }.toFloat()
                        val numerator = n * sumXY - sumX * sumY
                        val denominator = n * sumX2 - sumX * sumX
                        if (denominator != 0f) numerator / denominator else 0f
                    } else 0f
                    categoryTrends[rootCatId] = Pair(avg, trend)
                }
            
            val fixedCostsAvg = recentMonths.map { (_, monthTxs) ->
                monthTxs.filter { it.type == TransactionType.EXPENSE && it.isRecurring }
                    .sumOf { it.amount }.toFloat()
            }.average().toFloat()
            
            val lastLabel = sorted.last().key
            val lastMonthIdx = historySize - 1
            val lastMonthTxs = sorted.last().value

            // Historical point: actual values from last complete month
            val actualTopCategories = categoryTrends.entries
                .sortedByDescending { it.value.first }
                .take(5)
                .associate { (rootCatId, _) ->
                    val catName = categoryLabels[rootCatId] ?: "Sonstige"
                    val actualAmount = lastMonthTxs
                        .filter { getRootCategory(it.categoryId) == rootCatId && it.type == TransactionType.EXPENSE }
                        .sumOf { it.amount }.toFloat()
                    catName to actualAmount
                }
            val actualTotalExpenses = lastMonthTxs
                .filter { it.type == TransactionType.EXPENSE }
                .sumOf { it.amount }.toFloat()
            val actualFixedCosts = lastMonthTxs
                .filter { it.type == TransactionType.EXPENSE && it.isRecurring }
                .sumOf { it.amount }.toFloat()

            listOf(ForecastPoint(
                label = lastLabel,
                predicted = actualTotalExpenses,
                categoryForecasts = actualTopCategories,
                fixedCosts = actualFixedCosts,
                isHistorical = true
            )) + (1..3).map { offset ->
                val nextCal = parseMonthToCalendar(lastLabel).apply { add(Calendar.MONTH, offset) }
                val label = monthLabel(nextCal)
                val stepsFromMid = lastMonthIdx / 2f + offset
                val topCategories = categoryTrends.entries
                    .sortedByDescending { it.value.first }
                    .take(5)
                    .associate { (rootCatId, pair) ->
                        val (avg, trend) = pair
                        val catName = categoryLabels[rootCatId] ?: "Sonstige"
                        val predicted = (avg + trend * stepsFromMid).coerceAtLeast(0f)
                        catName to predicted
                    }
                val totalPredicted = categoryTrends.values.sumOf { (avg, trend) ->
                    (avg + trend * stepsFromMid).toDouble()
                }.toFloat().coerceAtLeast(0f)
                ForecastPoint(
                    label = label,
                    predicted = totalPredicted,
                    categoryForecasts = topCategories,
                    fixedCosts = fixedCostsAvg
                )
            }
        } else {
            // ── Config-driven: per-category trends → aggregate per config ──
            computeConfigForecast(cats, configs, categoryMap, recentMonths, sorted.last().key, historySize)
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun selectTimeRange(range: TimeRange) { selectedTimeRange.value = range }

    // ── Forecast Line Config Helpers ──

    private fun generateDefaultConfigs(txs: List<Transaction>, cats: List<Category>): List<ForecastLineConfig> {
        val monthly = groupByMonth(txs)
        val sorted = monthly.entries.sortedBy { parseMonthLabel(it.key) }
        if (sorted.size < 3) return emptyList()
        val historySize = if (sorted.size >= 6) 6 else 3
        val recentMonths = sorted.takeLast(historySize)
        val categoryMap = cats.associateBy { it.id }
        val rootCats = cats.filter { it.parentCategoryId == null || it.level == 1 }

        val rootAvgs = rootCats.map { cat ->
            val ids = getAllDescendantIds(cat.id, cats) + cat.id
            val avg = recentMonths.map { (_, monthTxs) ->
                monthTxs.filter { tx ->
                    tx.type == TransactionType.EXPENSE && tx.categoryId in ids
                }.sumOf { it.amount }.toFloat()
            }.average().toFloat()
            cat.id to avg
        }

        return rootAvgs.sortedByDescending { it.second }.take(5)
            .mapIndexed { idx, (catId, _) ->
                ForecastLineConfig(
                    id = UUID.randomUUID().toString(),
                    label = categoryMap[catId]?.name ?: "Kategorie ${idx + 1}",
                    categoryIds = setOf(catId),
                    colorIndex = idx
                )
            }
    }

    private fun computeConfigForecast(
        cats: List<Category>,
        configs: List<ForecastLineConfig>,
        categoryMap: Map<Long, Category>,
        recentMonths: List<Map.Entry<String, List<Transaction>>>,
        lastLabel: String,
        historySize: Int
    ): List<ForecastPoint> {
        // Compute per-category trends (each category including all descendants)
        val categoryTrends = mutableMapOf<Long, Pair<Float, Float>>()
        cats.forEach { cat ->
            val childIds = getAllDescendantIds(cat.id, cats) + cat.id
            val monthlyAmounts = recentMonths.map { (_, monthTxs) ->
                monthTxs.filter { tx ->
                    tx.type == TransactionType.EXPENSE && tx.categoryId in childIds
                }.sumOf { it.amount }.toFloat()
            }
            categoryTrends[cat.id] = linearRegression(monthlyAmounts)
        }

        // Fixed costs
        val fixedCostsAvg = recentMonths.map { (_, monthTxs) ->
            monthTxs.filter { it.type == TransactionType.EXPENSE && it.isRecurring }
                .sumOf { it.amount }.toFloat()
        }.average().toFloat()

        val lastMonthIdx = historySize - 1

        // Helper to resolve effective (topmost) category ids for a config
        fun resolveEffectiveIds(config: ForecastLineConfig): List<Long> =
            config.categoryIds.filter { catId ->
                var parent = categoryMap[catId]?.parentCategoryId
                var hasParent = false
                while (parent != null) {
                    if (parent in config.categoryIds) { hasParent = true; break }
                    parent = categoryMap[parent]?.parentCategoryId
                }
                !hasParent
            }

        // Historical point: actual values from last complete month
        val lastMonthEntry = recentMonths.last()
        val lastMonthTxs = lastMonthEntry.value

        val historicalConfigForecasts = mutableMapOf<String, Float>()
        for (config in configs) {
            if (config.categoryIds.isEmpty()) continue
            val effectiveIds = resolveEffectiveIds(config)
            val total = effectiveIds.sumOf { catId ->
                val childIds = getAllDescendantIds(catId, cats) + catId
                lastMonthTxs.filter { tx ->
                    tx.type == TransactionType.EXPENSE && tx.categoryId in childIds
                }.sumOf { it.amount }
            }.toFloat()
            historicalConfigForecasts[config.id] = total
        }

        val actualTotal = historicalConfigForecasts.values.sum()
        val actualFixedCosts = lastMonthTxs
            .filter { it.type == TransactionType.EXPENSE && it.isRecurring }
            .sumOf { it.amount }.toFloat()

        return listOf(ForecastPoint(
            label = lastLabel,
            predicted = actualTotal,
            categoryForecasts = historicalConfigForecasts,
            fixedCosts = actualFixedCosts,
            isHistorical = true
        )) + (1..3).map { offset ->
            val nextCal = parseMonthToCalendar(lastLabel).apply { add(Calendar.MONTH, offset) }
            val label = monthLabel(nextCal)
            val stepsFromMid = lastMonthIdx / 2f + offset

            val configForecasts = mutableMapOf<String, Float>()
            for (config in configs) {
                if (config.categoryIds.isEmpty()) continue
                val effectiveIds = resolveEffectiveIds(config)
                val total = effectiveIds.sumOf { catId ->
                    val (avg, trend) = categoryTrends[catId] ?: Pair(0f, 0f)
                    (avg + trend * stepsFromMid).coerceAtLeast(0f).toDouble()
                }.toFloat()
                configForecasts[config.id] = total
            }

            val totalPredicted = configForecasts.values.sum()

            ForecastPoint(
                label = label,
                predicted = totalPredicted,
                categoryForecasts = configForecasts,
                fixedCosts = fixedCostsAvg
            )
        }
    }

    // ── Helpers ──

    private fun cutoffMillis(monthsAgo: Int): Long {
        val cal = Calendar.getInstance().apply { add(Calendar.MONTH, -monthsAgo) }
        return cal.timeInMillis
    }

    private fun getAllDescendantIds(catId: Long, cats: List<Category>): Set<Long> {
        val children = cats.filter { it.parentCategoryId == catId }
        return children.flatMap { child ->
            getAllDescendantIds(child.id, cats) + child.id
        }.toSet()
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

internal fun linearRegression(monthlyAmounts: List<Float>): Pair<Float, Float> {
    if (monthlyAmounts.isEmpty()) return Pair(0f, 0f)
    val avg = monthlyAmounts.average().toFloat()
    val trend = if (monthlyAmounts.size >= 2) {
        val n = monthlyAmounts.size
        val sumX = (0 until n).sum().toFloat()
        val sumY = monthlyAmounts.sum()
        val sumXY = monthlyAmounts.mapIndexed { i, y -> i * y }.sum()
        val sumX2 = (0 until n).sumOf { it * it }.toFloat()
        val numerator = n * sumXY - sumX * sumY
        val denominator = n * sumX2 - sumX * sumX
        if (denominator != 0f) numerator / denominator else 0f
    } else 0f
    return Pair(avg, trend)
}
