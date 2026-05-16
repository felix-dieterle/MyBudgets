package de.mybudgets.app.ui.dashboard

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import dagger.hilt.android.AndroidEntryPoint
import de.mybudgets.app.R
import de.mybudgets.app.databinding.FragmentDashboardBinding
import de.mybudgets.app.ui.transactions.TransactionAdapter
import de.mybudgets.app.util.CurrencyFormatter
import de.mybudgets.app.viewmodel.DashboardViewModel
import de.mybudgets.app.viewmodel.TimeRange
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val vm: DashboardViewModel by viewModels()
    private lateinit var recentAdapter: TransactionAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recentAdapter = TransactionAdapter { item ->
            val bundle = Bundle().apply { putLong("transactionId", item.transaction.id) }
            findNavController().navigate(R.id.action_dashboardFragment_to_transactionDetailFragment, bundle)
        }
        binding.rvRecentTransactions.adapter = recentAdapter

        binding.tvSeeAllTransactions.setOnClickListener {
            findNavController().navigate(R.id.action_dashboardFragment_to_transactionsFragment)
        }

        setupCharts()
        setupTimeRangeChips()
        observeData()
    }

    private fun setupCharts() {
        with(binding.pieChart) {
            description.isEnabled = false
            isDrawHoleEnabled = true
            holeRadius = 40f
            setUsePercentValues(true)
            setEntryLabelTextSize(10f)
            legend.textSize = 11f
        }
        with(binding.barChartTrend) {
            description.isEnabled = false
            setFitBars(true)
            setDrawValueAboveBar(true)
            legend.textSize = 11f
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.granularity = 1f
            axisLeft.textSize = 10f
            axisRight.isEnabled = false
        }
        with(binding.barChartForecast) {
            description.isEnabled = false
            setFitBars(true)
            setDrawValueAboveBar(true)
            legend.textSize = 11f
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.granularity = 1f
            axisLeft.textSize = 10f
            axisRight.isEnabled = false
        }
    }

    private fun setupTimeRangeChips() {
        binding.chipGroupTimeRange.setOnCheckedStateChangeListener { _, checkedIds ->
            val id = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val range = when (id) {
                binding.chipMonth.id -> TimeRange.LAST_MONTH
                binding.chip3Months.id -> TimeRange.LAST_3_MONTHS
                else -> TimeRange.ALL
            }
            vm.selectTimeRange(range)
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    vm.totalBalance.collect { balance ->
                        binding.tvTotalBalance.text = CurrencyFormatter.format(balance ?: 0.0)
                    }
                }
                launch {
                    vm.accounts.collect { accounts ->
                        binding.tvAccountCount.text = "$accounts.size Konten"
                    }
                }
                launch {
                    vm.recentTransactions.collect { txList ->
                        recentAdapter.submitList(txList)
                        binding.tvNoRecentTransactions.visibility =
                            if (txList.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    vm.virtualOverview.collect { overview ->
                        binding.tvVirtualOverview.text = if (overview.isEmpty()) {
                            getString(R.string.dashboard_no_virtual_accounts)
                        } else {
                            overview.joinToString("\n") {
                                "$it.accountName: $CurrencyFormatter.format(it.balance) " +
                                    "(+$CurrencyFormatter.format(it.income) / -$CurrencyFormatter.format(it.expenses))"
                            }
                        }
                    }
                }
                launch {
                    vm.categoryChartData.collect { data ->
                        updatePieChart(data.pieEntries, data.categoryLabels)
                    }
                }
                launch {
                    vm.monthlyTrend.collect { trend ->
                        updateBarChartTrend(trend)
                    }
                }
                launch {
                    vm.forecast.collect { forecast ->
                        updateForecastChart(forecast)
                    }
                }
                launch {
                    vm.trendSummary.collect { summary ->
                        binding.tvTrendSummary.text = summary
                    }
                }
                launch {
                    vm.predictionWarnings.collect { warnings ->
                        binding.tvPredictionWarnings.text = warnings.joinToString("\n")
                            .ifBlank { getString(R.string.dashboard_no_warnings) }
                    }
                }
            }
        }
    }

    private fun updatePieChart(entries: List<com.github.mikephil.charting.data.PieEntry>, labels: Map<Long, String>) {
        if (entries.isEmpty()) {
            binding.pieChart.data = null
            binding.pieChart.invalidate()
            return
        }
        val colors = ColorTemplate.MATERIAL_COLORS.toMutableList()
        colors.addAll(ColorTemplate.JOYFUL_COLORS.toList())

        val dataSet = PieDataSet(entries, "").apply {
            setColors(colors)
            sliceSpace = 2f
            valueTextSize = 11f
            valueFormatter = PercentFormatter(binding.pieChart)
        }
        binding.pieChart.data = PieData(dataSet)
        binding.pieChart.invalidate()
    }

    private fun updateBarChartTrend(trend: List<de.mybudgets.app.viewmodel.MonthlyTrendPoint>) {
        if (trend.isEmpty()) {
            binding.barChartTrend.data = null
            binding.barChartTrend.invalidate()
            return
        }
        val incomeEntries = trend.mapIndexed { i, p -> BarEntry(i.toFloat(), p.income) }
        val expenseEntries = trend.mapIndexed { i, p -> BarEntry(i.toFloat(), p.expense) }

        val incomeColor = ContextCompat.getColor(requireContext(), R.color.income_green)
        val expenseColor = ContextCompat.getColor(requireContext(), R.color.expense_red)

        val incomeSet = BarDataSet(incomeEntries, "Einnahmen").apply {
            color = incomeColor
            setDrawValues(false)
        }
        val expenseSet = BarDataSet(expenseEntries, "Ausgaben").apply {
            color = expenseColor
            setDrawValues(false)
        }

        binding.barChartTrend.xAxis.valueFormatter = object : com.github.mikephil.charting.formatter.IndexAxisValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val idx = value.toInt()
                return if (idx in trend.indices) trend[idx].label else ""
            }
        }
        binding.barChartTrend.data = BarData(incomeSet, expenseSet).apply { barWidth = 0.4f }
        binding.barChartTrend.groupBars(-0.5f, 0.1f, 0.0f)
        binding.barChartTrend.invalidate()
    }

    private fun updateForecastChart(forecast: List<de.mybudgets.app.viewmodel.ForecastPoint>) {
        if (forecast.isEmpty()) {
            binding.barChartForecast.data = null
            binding.barChartForecast.invalidate()
            return
        }
        val entries = forecast.mapIndexed { i, p -> BarEntry(i.toFloat(), p.predicted) }
        val forecastColor = ContextCompat.getColor(requireContext(), R.color.expense_red)

        val dataSet = BarDataSet(entries, "Prognose").apply {
            color = forecastColor
            setDrawValues(false)
        }
        binding.barChartForecast.xAxis.valueFormatter = object : com.github.mikephil.charting.formatter.IndexAxisValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val idx = value.toInt()
                return if (idx in forecast.indices) forecast[idx].label else ""
            }
        }
        binding.barChartForecast.data = BarData(dataSet).apply { barWidth = 0.6f }
        binding.barChartForecast.invalidate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
