package de.mybudgets.app.ui.dashboard

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
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
import de.mybudgets.app.util.CurrencyFormatter
import de.mybudgets.app.viewmodel.CategoryChartData
import de.mybudgets.app.viewmodel.DashboardViewModel
import de.mybudgets.app.viewmodel.ForecastPoint
import de.mybudgets.app.viewmodel.MonthlyTrendPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ChartPageFragment : Fragment() {

    private val vm: DashboardViewModel by activityViewModels()
    private var _pageIndex: Int = 0
    private var currentTrend: List<MonthlyTrendPoint> = emptyList()

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

        chart.description.isEnabled = false
        chart.isDrawHoleEnabled = true
        chart.holeRadius = 40f
        chart.setUsePercentValues(true)
        chart.setEntryLabelTextSize(10f)
        chart.legend.textSize = 11f
        chart.setNoDataText(getString(R.string.chart_pie_empty))
        chart.setNoDataTextColor(Color.GRAY)

        chart.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
            override fun onValueSelected(e: Entry?, h: Highlight?) {
                val pieEntry = e as? PieEntry ?: return
                val catId = (pieEntry.data as? Int)?.toLong() ?: return
                vm.drillDownCategory(catId)
            }
            override fun onNothingSelected() {}
        })
        backBtn.setOnClickListener { vm.drillDownCategory(null) }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.categoryChartData.collect { data ->
                    updatePieChart(chart, data, catContainer, backBtn)
                }
            }
        }
    }

    private fun updatePieChart(
        chart: PieChart,
        data: CategoryChartData,
        catContainer: LinearLayout,
        backBtn: TextView
    ) {
        val entries = data.pieEntries
        if (entries.isEmpty()) {
            chart.data = null; chart.invalidate()
            catContainer.removeAllViews()
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

        if (data.drillDownParentName != null) {
            backBtn.text = "← ${data.drillDownParentName}"
            backBtn.visibility = View.VISIBLE
        } else {
            backBtn.visibility = View.GONE
        }

        catContainer.removeAllViews()
        for (entry in entries) {
            val catId = (entry.data as? Int)?.toLong() ?: continue
            val amount = data.categoryAmounts[catId] ?: 0f
            val isHidden = catId in vm.hiddenCategoryIds.value
            val cb = CheckBox(requireContext()).apply {
                isChecked = !isHidden
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            cb.setOnCheckedChangeListener { _, checked ->
                if (!checked) vm.toggleHideCategory(catId)
                else if (catId in vm.hiddenCategoryIds.value) vm.toggleHideCategory(catId)
            }
            val nameTv = TextView(requireContext()).apply {
                text = "${entry.label}: ${CurrencyFormatter.format(amount.toDouble())} (${"%.1f".format(entry.value)}%)"
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setTextColor(
                    if (isHidden) ContextCompat.getColor(requireContext(), android.R.color.darker_gray)
                    else ContextCompat.getColor(requireContext(), R.color.on_surface)
                )
                setOnClickListener {
                    val cId = (entry.data as? Int)?.toLong() ?: return@setOnClickListener
                    vm.drillDownCategory(cId)
                }
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
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.xAxis.granularity = 1f
        chart.axisLeft.textSize = 10f
        chart.axisRight.isEnabled = false
        chart.setNoDataText(getString(R.string.chart_trend_empty))
        chart.setNoDataTextColor(Color.GRAY)
        chart.setPinchZoom(true)
        chart.setScaleEnabled(true)

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
    }

    private fun renderTrendChart(chart: CombinedChart) {
        if (currentTrend.isEmpty()) {
            chart.data = null; chart.invalidate()
            return
        }
        val visibleSpan = (chart.highestVisibleX - chart.lowestVisibleX).toInt() + 1
        if (visibleSpan <= 4) renderCategoryBreakdown(chart)
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
        val warnings = root.findViewById<TextView>(R.id.tv_prediction_warnings)
        chart.description.isEnabled = false
        chart.legend.textSize = 10f
        chart.legend.isWordWrapEnabled = true
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.xAxis.granularity = 1f
        chart.xAxis.textSize = 10f
        chart.axisLeft.textSize = 10f
        chart.axisRight.isEnabled = false
        chart.setDrawGridBackground(false)
        chart.setNoDataText(getString(R.string.chart_forecast_empty))
        chart.setNoDataTextColor(Color.GRAY)

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.forecast.collect { f -> updateForecastChart(chart, f) } }
                launch { vm.predictionWarnings.collect { list ->
                    warnings?.text = list.joinToString("\n").ifBlank { getString(R.string.dashboard_no_warnings) }
                } }
            }
        }
    }

    private fun updateForecastChart(chart: LineChart, forecast: List<ForecastPoint>) {
        if (forecast.isEmpty()) { chart.data = null; chart.invalidate(); return }

        val dataSets = mutableListOf<LineDataSet>()
        val colors = listOf(
            ContextCompat.getColor(requireContext(), R.color.expense_red),
            ContextCompat.getColor(requireContext(), R.color.recurring_purple),
            ContextCompat.getColor(requireContext(), android.R.color.holo_orange_dark),
            ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark),
            ContextCompat.getColor(requireContext(), android.R.color.holo_blue_dark),
            ContextCompat.getColor(requireContext(), R.color.income_green)
        )

        val fixedCostsEntries = forecast.mapIndexed { i, p -> Entry(i.toFloat(), p.fixedCosts) }
        if (fixedCostsEntries.any { it.y > 0 }) {
            dataSets.add(LineDataSet(fixedCostsEntries, "Fixkosten").apply {
                color = colors[0]; lineWidth = 3f
                setDrawCircles(true); circleRadius = 4f; setCircleColor(colors[0])
                setDrawValues(false); enableDashedLine(10f, 5f, 0f)
            })
        }

        val allCategoryNames = forecast.flatMap { it.categoryForecasts.keys }.distinct()
        allCategoryNames.take(5).forEachIndexed { idx, catName ->
            val entries = forecast.mapIndexed { i, p -> Entry(i.toFloat(), p.categoryForecasts[catName] ?: 0f) }
            if (entries.any { it.y > 0 }) {
                dataSets.add(LineDataSet(entries, catName).apply {
                    color = colors.getOrElse(idx + 1) { colors.last() }; lineWidth = 2f
                    setDrawCircles(true); circleRadius = 3f; setCircleColor(color)
                    setDrawValues(false)
                })
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
}
