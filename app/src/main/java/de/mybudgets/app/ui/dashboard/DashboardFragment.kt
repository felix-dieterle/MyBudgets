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
import androidx.navigation.fragment.findNavController
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
    private val vm: DashboardViewModel by activityViewModels()
    private lateinit var recentAdapter: TransactionAdapter
    private lateinit var pagerAdapter: ChartPagerAdapter
    private val pageIndicatorViews = mutableListOf<View>()

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

        setupViewPager()
        setupTimeRangeChips()
        observeData()
    }

    private fun setupViewPager() {
        pagerAdapter = ChartPagerAdapter(childFragmentManager)
        binding.viewpagerCharts.adapter = pagerAdapter
        binding.viewpagerCharts.offscreenPageLimit = 2

        binding.layoutPageIndicator.removeAllViews()
        pageIndicatorViews.clear()
        repeat(3) { idx ->
            val dot = View(requireContext()).apply {
                setBackgroundResource(R.drawable.page_indicator_dot)
                val size = resources.getDimensionPixelSize(R.dimen.page_indicator_dot_size)
                val layoutParams = ViewGroup.MarginLayoutParams(size, size)
                layoutParams.setMargins(
                    resources.getDimensionPixelSize(R.dimen.page_indicator_dot_spacing),
                    0,
                    resources.getDimensionPixelSize(R.dimen.page_indicator_dot_spacing),
                    0
                )
                this.layoutParams = layoutParams
                isSelected = idx == 0
            }
            binding.layoutPageIndicator.addView(dot)
            pageIndicatorViews.add(dot)
        }

        binding.viewpagerCharts.addOnPageChangeListener(object : androidx.viewpager.widget.ViewPager.OnPageChangeListener {
            override fun onPageSelected(pos: Int) {
                pageIndicatorViews.forEachIndexed { i, v -> v.isSelected = i == pos }
            }
            override fun onPageScrolled(p0: Int, p1: Float, p2: Int) {}
            override fun onPageScrollStateChanged(p0: Int) {}
        })
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
                        binding.tvTotalBalance.setTextColor(
                            if ((balance ?: 0.0) >= 0.0) ContextCompat.getColor(requireContext(), R.color.income_green)
                            else ContextCompat.getColor(requireContext(), R.color.expense_red)
                        )
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
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
