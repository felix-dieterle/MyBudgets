package de.mybudgets.app.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import dagger.hilt.android.AndroidEntryPoint
import de.mybudgets.app.R
import de.mybudgets.app.viewmodel.DashboardViewModel
import de.mybudgets.app.viewmodel.ForecastPoint
import de.mybudgets.app.viewmodel.MonthlyTrendPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ChartPageFragment : Fragment() {

    private val vm: DashboardViewModel by activityViewModels()
    private var _pageIndex: Int = 0
    private var _root: View? = null

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
        return inflater.inflate(layout, container, false).also { _root = it }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        when (_pageIndex) {
            0 -> setupPieChart(view)
            1 -> setupTrendChart(view)
            2 -> setupForecastChart(view)
        }
    }

    private fun setupPieChart(root: View) {
        val chart = root.findViewById<com.github.mikephil.charting.charts.PieChart>(R.id.pie_chart) ?: return
        chart.description.isEnabled = false
        chart.isDrawHoleEnabled = true
        chart.holeRadius = 40f
        chart.setUsePercentValues(true)
        chart.setEntryLabelTextSize(10f)
        chart.legend.textSize = 11f
        chart.setNoDataText(getString(R.string.chart_pie_empty))
        chart.setNoDataTextColor(android.graphics.Color.GRAY)

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.categoryChartData.collect { data ->
                    updatePieChart(chart, data.pieEntries, data.categoryLabels)
                }
            }
        }
    }

    private fun setupTrendChart(root: View) {
        val chart = root.findViewById<com.github.mikephil.charting.charts.BarChart>(R.id.bar_chart_trend) ?: return
        val summary = root.findViewById<android.widget.TextView>(R.id.tv_trend_summary)
        chart.description.isEnabled = false
        chart.setFitBars(true)
        chart.setDrawValueAboveBar(true)
        chart.legend.textSize = 11f
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.xAxis.granularity = 1f
        chart.axisLeft.textSize = 10f
        chart.axisRight.isEnabled = false
        chart.setNoDataText(getString(R.string.chart_trend_empty))
        chart.setNoDataTextColor(android.graphics.Color.GRAY)

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.monthlyTrend.collect { t -> updateBarChartTrend(chart, t) } }
                launch { vm.trendSummary.collect { t -> summary?.text = t } }
            }
        }
    }

    private fun setupForecastChart(root: View) {
        val chart = root.findViewById<com.github.mikephil.charting.charts.LineChart>(R.id.line_chart_forecast) ?: return
        val warnings = root.findViewById<android.widget.TextView>(R.id.tv_prediction_warnings)
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
        chart.setNoDataTextColor(android.graphics.Color.GRAY)

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.forecast.collect { f -> updateForecastChart(chart, f) } }
                launch { vm.predictionWarnings.collect { list ->
                    warnings?.text = list.joinToString("\n").ifBlank { getString(R.string.dashboard_no_warnings) }
                } }
            }
        }
    }

    private fun updatePieChart(
        chart: com.github.mikephil.charting.charts.PieChart,
        entries: List<com.github.mikephil.charting.data.PieEntry>,
        labels: Map<Long, String>
    ) {
        if (entries.isEmpty()) {
            chart.data = null; chart.invalidate()
            return
        }
        val colors = ColorTemplate.MATERIAL_COLORS.toMutableList()
        colors.addAll(ColorTemplate.JOYFUL_COLORS.toList())
        val dataSet = PieDataSet(entries, "").apply {
            setColors(colors)
            sliceSpace = 2f
            valueTextSize = 11f
            valueFormatter = PercentFormatter(chart)
        }
        chart.data = PieData(dataSet)
        chart.invalidate()
    }

    private fun updateBarChartTrend(chart: com.github.mikephil.charting.charts.BarChart, trend: List<MonthlyTrendPoint>) {
        if (trend.isEmpty()) {
            chart.data = null; chart.invalidate()
            return
        }
        val incomeEntries = trend.mapIndexed { i, p -> BarEntry(i.toFloat(), p.income) }
        val expenseEntries = trend.mapIndexed { i, p -> BarEntry(i.toFloat(), p.expense) }
        val incomeColor = ContextCompat.getColor(requireContext(), R.color.income_green)
        val expenseColor = ContextCompat.getColor(requireContext(), R.color.expense_red)
        val incomeSet = BarDataSet(incomeEntries, "Einnahmen").apply {
            color = incomeColor; setDrawValues(false)
        }
        val expenseSet = BarDataSet(expenseEntries, "Ausgaben").apply {
            color = expenseColor; setDrawValues(false)
        }
        chart.xAxis.valueFormatter = object : IndexAxisValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val idx = value.toInt()
                return if (idx in trend.indices) trend[idx].label else ""
            }
        }
        chart.data = BarData(incomeSet, expenseSet).apply { barWidth = 0.4f }
        chart.groupBars(-0.5f, 0.1f, 0.0f)
        chart.invalidate()
    }

    private fun updateForecastChart(chart: com.github.mikephil.charting.charts.LineChart, forecast: List<ForecastPoint>) {
        if (forecast.isEmpty()) {
            chart.data = null; chart.invalidate()
            return
        }
        
        val dataSets = mutableListOf<LineDataSet>()
        val colors = listOf(
            ContextCompat.getColor(requireContext(), R.color.expense_red),
            ContextCompat.getColor(requireContext(), R.color.recurring_purple),
            ContextCompat.getColor(requireContext(), android.R.color.holo_orange_dark),
            ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark),
            ContextCompat.getColor(requireContext(), android.R.color.holo_blue_dark),
            ContextCompat.getColor(requireContext(), R.color.income_green)
        )
        
        // Fixed costs line (thicker, dashed)
        val fixedCostsEntries = forecast.mapIndexed { i, p -> Entry(i.toFloat(), p.fixedCosts) }
        if (fixedCostsEntries.any { it.y > 0 }) {
            dataSets.add(LineDataSet(fixedCostsEntries, "Fixkosten").apply {
                color = colors[0]
                lineWidth = 3f
                setDrawCircles(true)
                circleRadius = 4f
                setCircleColor(colors[0])
                setDrawValues(false)
                enableDashedLine(10f, 5f, 0f)
            })
        }
        
        // Top category lines
        val allCategoryNames = forecast.flatMap { it.categoryForecasts.keys }.distinct()
        allCategoryNames.take(5).forEachIndexed { idx, catName ->
            val entries = forecast.mapIndexed { i, p ->
                Entry(i.toFloat(), p.categoryForecasts[catName] ?: 0f)
            }
            if (entries.any { it.y > 0 }) {
                dataSets.add(LineDataSet(entries, catName).apply {
                    color = colors.getOrElse(idx + 1) { colors.last() }
                    lineWidth = 2f
                    setDrawCircles(true)
                    circleRadius = 3f
                    setCircleColor(color)
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

    override fun onDestroyView() {
        super.onDestroyView()
        _root = null
    }
}
