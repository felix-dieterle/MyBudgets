package de.mybudgets.app.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
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

        recentAdapter = TransactionAdapter { tx ->
            val bundle = Bundle().apply { putLong("transactionId", tx.id) }
            findNavController().navigate(R.id.action_dashboardFragment_to_transactionDetailFragment, bundle)
        }
        binding.rvRecentTransactions.adapter = recentAdapter

        binding.tvSeeAllTransactions.setOnClickListener {
            findNavController().navigate(R.id.action_dashboardFragment_to_transactionsFragment)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    vm.totalBalance.collect { balance ->
                        binding.tvTotalBalance.text = CurrencyFormatter.format(balance ?: 0.0)
                    }
                }
                launch {
                    vm.accounts.collect { accounts ->
                        binding.tvAccountCount.text = "${accounts.size} Konten"
                    }
                }
                launch {
                    vm.recentTransactions.collect { txList ->
                        recentAdapter.submitList(txList)
                        binding.tvNoRecentTransactions.visibility =
                            if (txList.isEmpty()) View.VISIBLE else View.GONE
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
