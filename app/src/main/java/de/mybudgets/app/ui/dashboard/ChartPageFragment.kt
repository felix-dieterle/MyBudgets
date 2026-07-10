package de.mybudgets.app.ui.dashboard

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.CombinedData
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.listener.ChartTouchListener
import com.github.mikephil.charting.listener.OnChartGestureListener
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.github.mikephil.charting.utils.ColorTemplate
import com.github.mikephil.charting.highlight.Highlight
import dagger.hilt.android.AndroidEntryPoint
import de.mybudgets.app.R
import de.mybudgets.app.util.AppLogger
import de.mybudgets.app.util.CurrencyFormatter
import de.mybudgets.app.viewmodel.CategoryChartData
import de.mybudgets.app.viewmodel.DashboardViewModel
import de.mybudgets.app.viewmodel.DonutDisplayMode
import de.mybudgets.app.viewmodel.DonutSliceConfig
import de.mybudgets.app.viewmodel.ForecastLineConfig
import de.mybudgets.app.viewmodel.ForecastPoint
import de.mybudgets.app.viewmodel.MonthlyTrendPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ChartPageFragment : Fragment() {

    private val vm: DashboardViewModel by activityViewModels()
    private var _pageIndex: Int = 0
    private var currentTrend: List<MonthlyTrendPoint> = emptyList()
    private var currentForecast: List<ForecastPoint> = emptyList()
    private var configOrder: List<String> = emptyList()
    private var pendingTapLabel: String? = null

    private val configColors: List<Int> by lazy {
        listOf(
            ContextCompat.getColor(requireContext(), R.color.recurring_purple),
            ContextCompat.getColor(requireContext(), android.R.color.holo_orange_dark),
            ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark),
            ContextCompat.getColor(requireContext(), android.R.color.holo_blue_dark),
            ContextCompat.getColor(requireContext(), R.color.income_green),
            ContextCompat.getColor(requireContext(), R.color.expense_red),
            0xFF5C6BC0.toInt(),
            0xFF66BB6A.toInt(),
            0xFF42A5F5.toInt(),
            0xFFFF7043.toInt()
        )
    }

    private val catColors = listOf(
        0xFF5C6BC0.toInt(), 0xFF66BB6A.toInt(), 0xFF42A5F5.toInt(),
        0xFFFF7043.toInt(), 0xFF26C6DA.toInt(), 0xFF8D6E63.toInt(),
        0xFFAB47BC.toInt(), 0xFF7B1FA2.toInt(), 0xFFEF5350.toInt(),
        0xFFFFA726.toInt()
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _pageIndex = arguments?.getInt("pageIndex") ?: 0
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val layout = when (_pageIndex) {
            0 -> R.layout.chart_page_pie
            1 -> R.layout.chart_page_trend
            else -> R.layout.chart_page_forecast
        }
        return inflater.inflate(layout, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        when (_pageIndex) {
            0 -> setupPieChart(view)
            1 -> setupTrendChart(view)
            2 -> setupForecastChart(view)
        }
    }

    // ── Pie Chart ──

    private fun setupPieChart(root: View) {
        val chart = root.findViewById<PieChart>(R.id.pie_chart) ?: return
        val catContainer = root.findViewById<LinearLayout>(R.id.layout_pie_categories)
        val backBtn = root.findViewById<TextView>(R.id.tv_pie_back)
        val modeBtn = root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_donut_mode)
        val resetBtn = root.findViewById<ImageButton>(R.id.btn_reset_donut_slices)
        val addBtn = root.findViewById<ImageButton>(R.id.btn_add_donut_slice)
        val totalSumView = root.findViewById<TextView>(R.id.tv_pie_total_sum)

        chart.description.isEnabled = false
        chart.isDrawHoleEnabled = true
        chart.holeRadius = 40f
        chart.setUsePercentValues(true)
        chart.setEntryLabelTextSize(10f)
        chart.legend.textSize = 11f
        chart.setExtraBottomOffset(15f)
        chart.setNoDataText(getString(R.string.chart_pie_empty))
        chart.setNoDataTextColor(Color.GRAY)

        chart.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
            override fun onValueSelected(e: Entry?, h: Highlight?) {
                val pieEntry = e as? PieEntry ?: return
                if (vm.donutDisplayMode.value == DonutDisplayMode.CUSTOM_SETS && vm.drillFromSetId.value == null) {
                    val configId = pieEntry.data as? String ?: return
                    vm.setDrillFromSet(configId)
                } else {
                    val catId = (pieEntry.data as? Int)?.toLong() ?: return
                    vm.drillDownCategory(catId)
                }
            }
            override fun onNothingSelected() {}
        })

        backBtn.setOnClickListener {
            if (vm.donutDisplayMode.value == DonutDisplayMode.CUSTOM_SETS) {
                if (vm.drillFromSetId.value != null) {
                    vm.setDrillFromSet(null)
                } else {
                    vm.setDonutDisplayMode(DonutDisplayMode.CATEGORIES)
                }
            } else {
                vm.drillDownCategory(null)
            }
        }

        modeBtn.setOnClickListener { vm.setDonutDisplayMode(DonutDisplayMode.CUSTOM_SETS) }
        resetBtn.setOnClickListener { vm.resetDonutConfigs() }
        addBtn.setOnClickListener {
            val config = DonutSliceConfig(
                id = java.util.UUID.randomUUID().toString(),
                label = "Neues Set",
                categoryIds = emptySet()
            )
            vm.saveDonutSliceConfig(config)
            DonutSliceEditDialogFragment.newInstance(
                config = config,
                allCategories = vm.allCategories.value,
                allConfigs = vm.donutSliceConfigs.value
            ).show(parentFragmentManager, "donut_slice_edit")
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.categoryChartData.collect { data ->
                    updatePieChart(chart, data, catContainer, backBtn, totalSumView)
                } }
                launch { combine(vm.donutDisplayMode, vm.donutSliceConfigs) { mode, configs -> Pair(mode, configs) }.collect { (mode, configs) ->
                    modeBtn.visibility = if (mode != DonutDisplayMode.CUSTOM_SETS) View.VISIBLE else View.GONE
                    addBtn.visibility = if (mode == DonutDisplayMode.CUSTOM_SETS) View.VISIBLE else View.GONE
                    resetBtn.visibility = if (configs.isNotEmpty() && mode == DonutDisplayMode.CUSTOM_SETS) View.VISIBLE else View.GONE
                } }
            }
        }
    }

    private fun updatePieChart(
        chart: PieChart,
        data: CategoryChartData,
        catContainer: LinearLayout,
        backBtn: TextView,
        totalSumView: TextView
    ) {
        val entries = data.pieEntries
        if (entries.isEmpty()) {
            chart.data = null; chart.invalidate()
            catContainer.removeAllViews()
            totalSumView.text = ""
            return
        }
        val colors = catColors.toMutableList()
        colors.addAll(ColorTemplate.MATERIAL_COLORS.toList())
        val dataSet = PieDataSet(entries, "").apply {
            setColors(colors)
            sliceSpace = 2f
            valueTextSize = 11f
            valueFormatter = PercentFormatter(chart)
        }
        chart.data = PieData(dataSet)
        chart.invalidate()

        val isCustomMode = vm.donutDisplayMode.value == DonutDisplayMode.CUSTOM_SETS
        val isDrillFromSet = vm.drillFromSetId.value != null

        if (isCustomMode && !isDrillFromSet) {
            backBtn.text = getString(R.string.chart_pie_back_categories)
            backBtn.visibility = View.VISIBLE
        } else if (data.drillDownParentName != null) {
            backBtn.text = "← ${data.drillDownParentName}"
            backBtn.visibility = View.VISIBLE
        } else {
            backBtn.visibility = View.GONE
        }

        // Income/expense total
        if (data.incomeTotal > 0f || data.expenseTotal > 0f) {
            val incColor = ContextCompat.getColor(requireContext(), R.color.income_green)
            val expColor = ContextCompat.getColor(requireContext(), R.color.expense_red)
            val net = data.incomeTotal - data.expenseTotal
            val netColor = ContextCompat.getColor(requireContext(), if (net >= 0) R.color.income_green else R.color.expense_red)
            val sb = SpannableStringBuilder()
            val incStr = "Einnahmen: +${CurrencyFormatter.format(data.incomeTotal.toDouble())} €"
            val startInc = sb.length
            sb.append(incStr)
            sb.setSpan(ForegroundColorSpan(incColor), startInc, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.append(" | ")
            val expStr = "Ausgaben: -${CurrencyFormatter.format(data.expenseTotal.toDouble())} €"
            val startExp = sb.length
            sb.append(expStr)
            sb.setSpan(ForegroundColorSpan(expColor), startExp, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.append(" | ")
            val netStr = "Saldo: ${CurrencyFormatter.format(net.toDouble())} €"
            val startNet = sb.length
            sb.append(netStr)
            sb.setSpan(ForegroundColorSpan(netColor), startNet, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (data.monthCount > 1) {
                sb.append(" | Ø/Monat: ${CurrencyFormatter.format((net / data.monthCount).toDouble())} €")
            }
            totalSumView.text = sb
        } else {
            totalSumView.text = ""
        }

        catContainer.removeAllViews()
        if (isCustomMode && !isDrillFromSet) {
            // Showing slices: show config labels
            for ((cId, amount) in data.categoryAmounts.entries.sortedByDescending { it.value }) {
                val name = data.categoryLabels[cId] ?: "Unbekannt"
                val total = data.categoryAmounts.values.sum().coerceAtLeast(1f)
                val pct = amount / total * 100f
                val tv = TextView(requireContext()).apply {
                    text = "$name: ${CurrencyFormatter.format(amount.toDouble())} (${"%.1f".format(pct)}%)"
                    textSize = 13f
                    setPadding(0, 4, 0, 4)
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.on_surface))
                }
                catContainer.addView(tv)
            }
            return
        }

        val hiddenIds = vm.hiddenCategoryIds.value
        for (cId in data.levelCategoryIds) {
            val amount = data.categoryAmounts[cId] ?: 0f
            val name = data.categoryLabels[cId] ?: "Sonstige"
            val matchingEntry = entries.find { (it.data as? Int)?.toLong() == cId }
            val pct = matchingEntry?.value ?: 0f
            val isHidden = cId in hiddenIds
            val cb = CheckBox(requireContext()).apply {
                isChecked = !isHidden
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            cb.setOnCheckedChangeListener { _, _ -> vm.toggleHideCategory(cId) }
            val nameTv = TextView(requireContext()).apply {
                text = buildString {
                    append(name)
                    if (amount > 0f) append(": ${CurrencyFormatter.format(amount.toDouble())} (${"%.1f".format(pct)}%)")
                }
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setTextColor(
                    if (isHidden) ContextCompat.getColor(requireContext(), android.R.color.darker_gray)
                    else ContextCompat.getColor(requireContext(), R.color.on_surface)
                )
                setOnClickListener { vm.drillDownCategory(cId) }
            }
            val rowLayout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setPadding(0, 4, 0, 4)
                addView(cb)
                addView(nameTv)
            }
            catContainer.addView(rowLayout)
        }
    }

    // ── Trend Chart (CombinedChart with zoom + L1 breakdown) ──

    private fun setupTrendChart(root: View) {
        val chart = root.findViewById<CombinedChart>(R.id.bar_chart_trend) ?: return
        val summary = root.findViewById<TextView>(R.id.tv_trend_summary)

        chart.description.isEnabled = false
        chart.legend.textSize = 11f
        chart.setExtraBottomOffset(15f)
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.xAxis.granularity = 1f
        chart.axisLeft.textSize = 10f
        chart.axisRight.isEnabled = false
        chart.setNoDataText(getString(R.string.chart_trend_empty))
        chart.setNoDataTextColor(Color.GRAY)
        chart.setPinchZoom(true)
        chart.setScaleXEnabled(true)
        chart.setScaleYEnabled(false)

        chart.setOnChartGestureListener(object : OnChartGestureListener {
            override fun onChartGestureStart(event: MotionEvent?, lastPerformedGesture: ChartTouchListener.ChartGesture?) {}
            override fun onChartGestureEnd(event: MotionEvent?, lastPerformedGesture: ChartTouchListener.ChartGesture?) {}
            override fun onChartLongPressed(event: MotionEvent?) {}
            override fun onChartDoubleTapped(event: MotionEvent?) {}
            override fun onChartSingleTapped(event: MotionEvent?) {}
            override fun onChartFling(event1: MotionEvent?, event2: MotionEvent?, velocityX: Float, velocityY: Float) {}
            override fun onChartScale(event: MotionEvent?, scaleX: Float, scaleY: Float) { renderTrendChart(chart) }
            override fun onChartTranslate(event: MotionEvent?, translateX: Float, translateY: Float) { renderTrendChart(chart) }
        })

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.monthlyTrend.collect { t ->
                    currentTrend = t
                    renderTrendChart(chart)
                } }
                launch { vm.trendSummary.collect { t -> summary?.text = t } }
            }
        }

        root.findViewById<ImageButton>(R.id.btn_export_trend)?.setOnClickListener { exportTrendData() }
    }

    private fun renderTrendChart(chart: CombinedChart) {
        if (currentTrend.isEmpty()) {
            chart.data = null; chart.invalidate()
            return
        }
        val span = chart.highestVisibleX - chart.lowestVisibleX
        val visibleMonths = if (span > 0f && span < currentTrend.size.toFloat() * 2f) (span.toInt() + 1) else currentTrend.size
        if (visibleMonths <= 4) renderCategoryBreakdown(chart)
        else renderIncomeExpenseWithBalance(chart)
    }

    private fun renderIncomeExpenseWithBalance(chart: CombinedChart) {
        val trend = currentTrend
        val barWidth = 0.18f
        val offset = 0.12f

        val incomeEntries = trend.mapIndexed { i, p -> BarEntry(i.toFloat() - offset, p.income) }
        val expenseEntries = trend.mapIndexed { i, p -> BarEntry(i.toFloat() + offset, p.expense) }
        val incomeColor = ContextCompat.getColor(requireContext(), R.color.income_green)
        val expenseColor = ContextCompat.getColor(requireContext(), R.color.expense_red)
        val incomeSet = BarDataSet(incomeEntries, "Einnahmen").apply { color = incomeColor; setDrawValues(false) }
        val expenseSet = BarDataSet(expenseEntries, "Ausgaben").apply { color = expenseColor; setDrawValues(false) }

        val balEntries = trend.mapIndexed { i, p -> Entry(i.toFloat(), p.balance) }
        val balColor = ContextCompat.getColor(requireContext(), R.color.transfer_blue)
        val balanceSet = LineDataSet(balEntries, "Kontostand").apply {
            color = balColor; lineWidth = 2f
            setDrawCircles(true); circleRadius = 3f; setCircleColor(balColor)
            setDrawValues(false)
            axisDependency = YAxis.AxisDependency.RIGHT
        }

        chart.xAxis.valueFormatter = object : IndexAxisValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val idx = value.toInt()
                return if (idx in trend.indices) trend[idx].label else ""
            }
        }
        chart.axisRight.isEnabled = true
        chart.axisRight.textSize = 10f

        val combined = CombinedData().apply {
            setData(BarData(incomeSet, expenseSet).apply { this.barWidth = barWidth })
            setData(LineData(balanceSet))
        }
        chart.data = combined
        chart.invalidate()
    }

    private fun renderCategoryBreakdown(chart: CombinedChart) {
        val trend = currentTrend

        val allCatNames = trend.flatMap { it.categoryExpenses.keys }.distinct().sorted()
        if (allCatNames.isEmpty()) { renderIncomeExpenseWithBalance(chart); return }

        val n = allCatNames.size
        val span = 0.5f
        val bw = span / n

        val dataSets = allCatNames.mapIndexed { ci, catName ->
            val entries = trend.mapIndexed { mi, p ->
                val x = mi.toFloat() - span / 2f + ci * bw + bw / 2f
                BarEntry(x, p.categoryExpenses[catName] ?: 0f)
            }
            BarDataSet(entries, catName).apply {
                color = catColors[ci % catColors.size]
                setDrawValues(false)
            }
        }

        val balEntries = trend.mapIndexed { i, p -> Entry(i.toFloat(), p.balance) }
        val balColor = ContextCompat.getColor(requireContext(), R.color.transfer_blue)
        val balanceSet = LineDataSet(balEntries, "Kontostand").apply {
            color = balColor; lineWidth = 2f
            setDrawCircles(true); circleRadius = 3f; setCircleColor(balColor)
            setDrawValues(false)
            axisDependency = YAxis.AxisDependency.RIGHT
        }

        chart.xAxis.valueFormatter = object : IndexAxisValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val idx = value.toInt()
                return if (idx in trend.indices) trend[idx].label else ""
            }
        }
        chart.axisRight.isEnabled = true
        chart.axisRight.textSize = 10f

        val combined = CombinedData().apply {
            setData(BarData(dataSets as List<com.github.mikephil.charting.interfaces.datasets.IBarDataSet>).apply {
                this.barWidth = bw
            })
            setData(LineData(balanceSet))
        }
        chart.data = combined
        chart.invalidate()
    }

    // ── Forecast Chart ──

    private fun setupForecastChart(root: View) {
        val chart = root.findViewById<LineChart>(R.id.line_chart_forecast) ?: return
        val legendContainer = root.findViewById<LinearLayout>(R.id.layout_forecast_legend)
        val warnings = root.findViewById<TextView>(R.id.tv_prediction_warnings)
        chart.description.isEnabled = false
        chart.legend.textSize = 10f
        chart.legend.isWordWrapEnabled = true
        chart.setExtraBottomOffset(25f)
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.xAxis.granularity = 1f
        chart.xAxis.textSize = 10f
        chart.axisLeft.textSize = 10f
        chart.axisRight.isEnabled = false
        chart.setDrawGridBackground(false)
        chart.setHighlightPerTapEnabled(true)
        chart.setNoDataText(getString(R.string.chart_forecast_empty))
        chart.setNoDataTextColor(Color.GRAY)

        chart.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
            override fun onValueSelected(e: Entry?, h: Highlight?) {
                val idx = h?.dataSetIndex ?: return
                // dataSetIndex 0 = fixed costs line (skip)
                if (idx == 0) return

                val configs = vm.forecastLineConfigs.value
                if (configs.isEmpty()) {
                    // Legacy mode → get category name from forecast keys
                    val allNames = currentForecast.flatMap { it.categoryForecasts.keys }.distinct()
                    val tappedName = allNames.getOrNull(idx - 1) ?: return
                    pendingTapLabel = tappedName
                    vm.initializeDefaultConfigs()
                    return
                }

                val configIdx = idx - 1
                if (configIdx < 0 || configIdx >= configOrder.size) return
                val configId = configOrder[configIdx]
                val config = configs.find { it.id == configId }
                if (config != null) {
                    showLineEditor(config)
                }
            }
            override fun onNothingSelected() {}
        })

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.forecast.collect { f ->
                    currentForecast = f
                    updateForecastChart(chart, legendContainer)
                } }
                launch { vm.forecastLineConfigs.collect { configs ->
                    val hadPending = pendingTapLabel != null
                    if (configs.isNotEmpty() && pendingTapLabel != null) {
                        val label = pendingTapLabel!!
                        pendingTapLabel = null
                        AppLogger.d("ForecastCfg", "pendingTap label=$label")
                        val config = configs.find { it.label == label }
                        AppLogger.d("ForecastCfg", "found by label=${config?.label} id=${config?.id}")
                        if (config != null) {
                            showLineEditor(config)
                        }
                    }
                    if (!hadPending) updateForecastChart(chart, legendContainer)
                } }
                launch { vm.predictionWarnings.collect { list ->
                    warnings?.text = list.joinToString("\n").ifBlank { getString(R.string.dashboard_no_warnings) }
                } }
            }
        }

        root.findViewById<ImageButton>(R.id.btn_reset_forecast_lines)?.setOnClickListener {
            vm.resetForecastConfigs()
        }
        root.findViewById<ImageButton>(R.id.btn_export_forecast)?.setOnClickListener { exportForecastData() }
    }

    private fun updateForecastChart(chart: LineChart, legendContainer: LinearLayout?) {
        val forecast = currentForecast
        legendContainer?.removeAllViews()
        if (forecast.isEmpty()) { chart.data = null; chart.invalidate(); return }

        val ctx = requireContext()
        val configs = vm.forecastLineConfigs.value
        val isConfigDriven = configs.isNotEmpty()
        val dataSets = mutableListOf<LineDataSet>()
        val historicalCount = forecast.count { it.isHistorical }
        configOrder = emptyList()

        // Helper: add legend row
        //   configId = UUID → config-driven click (opens editor for that config)
        //   configId = catName (not a UUID) → legacy click (inits configs, then opens editor)
        //   configId = null → not clickable (fixed costs)
        fun addLegendRow(color: Int, label: String, amount: Float, configId: String?) {
            val ss = SpannableString("●  $label: ${CurrencyFormatter.format(amount.toDouble())}")
            ss.setSpan(ForegroundColorSpan(color), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            val tv = TextView(ctx).apply {
                text = ss
                textSize = 13f
                setPadding(0, 4, 0, 4)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                tag = configId
                if (configId != null) {
                    setTextColor(ContextCompat.getColor(ctx, R.color.on_surface))
                    setOnClickListener { v ->
                        val id = v?.tag as? String ?: return@setOnClickListener
                        val allConfigs = vm.forecastLineConfigs.value
                        AppLogger.d("LegendClick", "tag=$id configs=${allConfigs.size} ids=${allConfigs.map{it.id}} labels=${allConfigs.map{it.label}}")
                        val config = allConfigs.find { it.id == id }
                        AppLogger.d("LegendClick", "found=${config?.label}")
                        if (config != null) {
                            showLineEditor(config)
                        } else {
                            pendingTapLabel = id
                            vm.initializeDefaultConfigs()
                        }
                    }
                } else {
                    setTextColor(ContextCompat.getColor(ctx, android.R.color.darker_gray))
                }
            }
            legendContainer?.addView(tv)
        }

        // ── Fixed costs line (always first, dataSetIndex=0) ──
        val fixedCostsColor = ContextCompat.getColor(ctx, R.color.expense_red)
        val fixedCostsEntries = forecast.mapIndexed { i, p -> Entry(i.toFloat(), p.fixedCosts) }
        val hasFixedCosts = fixedCostsEntries.any { it.y > 0 }
        if (hasFixedCosts) {
            dataSets.add(LineDataSet(fixedCostsEntries, "Fixkosten").apply {
                color = fixedCostsColor; lineWidth = 3f
                setDrawCircles(true); circleRadius = 4f
                setCircleColors(List(fixedCostsEntries.size) { i ->
                    if (i < historicalCount) fixedCostsColor
                    else Color.argb(80, Color.red(fixedCostsColor), Color.green(fixedCostsColor), Color.blue(fixedCostsColor))
                })
                setDrawValues(false); enableDashedLine(10f, 5f, 0f)
                setHighlightEnabled(false)
            })
            addLegendRow(fixedCostsColor, "Fixkosten", forecast.maxOf { it.fixedCosts }, null)
        }

        // ── Config-driven lines ──
        if (isConfigDriven) {
            val configOrderList = mutableListOf<String>()
            for (config in configs) {
                if (config.categoryIds.isEmpty()) continue
                val cfgId = config.id
                val cfgLabel = config.label
                val cfgColorIdx = config.colorIndex
                val entries = forecast.mapIndexed { i, p ->
                    Entry(i.toFloat(), p.categoryForecasts[cfgId] ?: 0f).apply { data = cfgId }
                }
                if (entries.any { it.y > 0 }) {
                    val color = configColors[cfgColorIdx % configColors.size]
                    dataSets.add(LineDataSet(entries, cfgLabel).apply {
                        this.color = color; lineWidth = 2f
                        setDrawCircles(true); circleRadius = 3f
                        setCircleColors(List(entries.size) { i ->
                            if (i < historicalCount) color
                            else Color.argb(80, Color.red(color), Color.green(color), Color.blue(color))
                        })
                    })
                    configOrderList.add(cfgId)
                    val latestVal = forecast.lastOrNull()?.categoryForecasts?.get(cfgId) ?: 0f
                    addLegendRow(color, cfgLabel, latestVal, cfgId)
                }
            }
            configOrder = configOrderList
        } else {
            // ── Legacy fallback: top 5 from categoryForecasts keys ──
            val allNames = forecast.flatMap { it.categoryForecasts.keys }.distinct()
            val legacyColors = listOf(
                ContextCompat.getColor(ctx, R.color.recurring_purple),
                ContextCompat.getColor(ctx, android.R.color.holo_orange_dark),
                ContextCompat.getColor(ctx, android.R.color.holo_green_dark),
                ContextCompat.getColor(ctx, android.R.color.holo_blue_dark),
                ContextCompat.getColor(ctx, R.color.income_green)
            )
            val namesList = allNames.take(5)
            for (idx in namesList.indices) {
                val catName = namesList[idx]
                val entries = forecast.mapIndexed { i, p ->
                    Entry(i.toFloat(), p.categoryForecasts[catName] ?: 0f)
                }
                if (entries.any { it.y > 0 }) {
                    val color = legacyColors.getOrElse(idx) { legacyColors.last() }
                    dataSets.add(LineDataSet(entries, catName).apply {
                        this.color = color; lineWidth = 2f
                        setDrawCircles(true); circleRadius = 3f
                        setCircleColors(List(entries.size) { i ->
                            if (i < historicalCount) color
                            else Color.argb(80, Color.red(color), Color.green(color), Color.blue(color))
                        })
                        setDrawValues(false)
                    })
                    val latestVal = forecast.lastOrNull()?.categoryForecasts?.get(catName) ?: 0f
                    addLegendRow(color, catName, latestVal, catName)
                }
            }
        }

        chart.xAxis.valueFormatter = object : IndexAxisValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val idx = value.toInt()
                return if (idx in forecast.indices) forecast[idx].label else ""
            }
        }
        chart.data = LineData(dataSets as List<com.github.mikephil.charting.interfaces.datasets.ILineDataSet>)
        chart.invalidate()
    }

    private fun showLineEditor(config: ForecastLineConfig) {
        val cats = vm.allCategories.value
        val allConfigs = vm.forecastLineConfigs.value
        val idx = allConfigs.indexOfFirst { it.id == config.id }
        ForecastLineEditDialogFragment.newInstance(
            config = config,
            allCategories = cats,
            allConfigs = allConfigs,
            currentIndex = idx
        ).apply {
            setOnSaveListener { updatedConfig ->
                vm.saveLineConfig(updatedConfig)
            }
            setOnDeleteListener { configId ->
                vm.removeLineConfig(configId)
            }
            setOnNavigateListener { newIndex ->
                val fresh = vm.forecastLineConfigs.value
                if (newIndex in fresh.indices) {
                    showLineEditor(fresh[newIndex])
                }
            }
        }.show(parentFragmentManager, "edit_forecast_line")
    }

    // ── Export ──

    private fun exportTrendData() {
        val trend = currentTrend
        if (trend.isEmpty()) return
        val allCats = trend.flatMap { it.categoryExpenses.keys }.distinct().sorted()
        val header = buildString {
            append("Monat;Einnahmen;Ausgaben;Saldo")
            for (cat in allCats) append(";$cat")
            appendLine()
        }
        val rows = trend.joinToString("") { p ->
            buildString {
                append(p.label)
                append(";${CurrencyFormatter.format(p.income.toDouble())}")
                append(";${CurrencyFormatter.format(p.expense.toDouble())}")
                append(";${CurrencyFormatter.format(p.balance.toDouble())}")
                for (cat in allCats) {
                    append(";${CurrencyFormatter.format((p.categoryExpenses[cat] ?: 0f).toDouble())}")
                }
                appendLine()
            }
        }
        shareCsv("Monatlicher-Verlauf.csv", header + rows)
    }

    private fun exportForecastData() {
        val forecast = currentForecast
        if (forecast.isEmpty()) return
        val allCats = forecast.flatMap { it.categoryForecasts.keys }.distinct().sorted()
        val header = buildString {
            append("Monat;Fixkosten")
            for (cat in allCats) append(";$cat")
            append(";Typ")
            appendLine()
        }
        val rows = forecast.joinToString("") { p ->
            buildString {
                append(p.label)
                append(";${CurrencyFormatter.format(p.fixedCosts.toDouble())}")
                for (cat in allCats) {
                    append(";${CurrencyFormatter.format((p.categoryForecasts[cat] ?: 0f).toDouble())}")
                }
                append(";${if (p.isHistorical) "Historisch" else "Prognose"}")
                appendLine()
            }
        }
        shareCsv("Prognose.csv", header + rows)
    }

    private fun shareCsv(filename: String, csv: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, filename)
            putExtra(Intent.EXTRA_TEXT, csv)
        }
        startActivity(Intent.createChooser(intent, "Export: $filename"))
    }
}
