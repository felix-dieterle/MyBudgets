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
import dagger.hilt.android.AndroidEntryPoint
import de.mybudgets.app.databinding.FragmentDashboardBinding
import de.mybudgets.app.util.CurrencyFormatter
import de.mybudgets.app.viewmodel.DashboardViewModel
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val vm: DashboardViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
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
                    vm.transactions.collect { txList ->
                        binding.tvTransactionCount.text = "${txList.size} Buchungen"
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
