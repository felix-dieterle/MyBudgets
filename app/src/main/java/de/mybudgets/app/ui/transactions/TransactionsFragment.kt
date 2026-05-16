package de.mybudgets.app.ui.transactions

import android.app.DatePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import de.mybudgets.app.R
import de.mybudgets.app.databinding.FragmentTransactionsBinding
import de.mybudgets.app.util.DateFormatter
import de.mybudgets.app.viewmodel.TransactionViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Calendar

@AndroidEntryPoint
class TransactionsFragment : Fragment() {

    private var _binding: FragmentTransactionsBinding? = null
    private val binding get() = _binding!!
    private val vm: TransactionViewModel by viewModels()
    private lateinit var adapter: TransactionAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _binding = FragmentTransactionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = TransactionAdapter { item ->
            val bundle = Bundle().apply { putLong("transactionId", item.transaction.id) }
            findNavController().navigate(R.id.action_transactionsFragment_to_transactionDetailFragment, bundle)
        }
        binding.rvTransactions.adapter = adapter
        binding.fabAddTransaction.setOnClickListener {
            findNavController().navigate(R.id.action_transactionsFragment_to_addEditTransactionFragment)
        }
        binding.fabNewTransfer.setOnClickListener {
            findNavController().navigate(R.id.action_transactionsFragment_to_transferFragment)
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { vm.setSearchQuery(s?.toString() ?: "") }
        })

        binding.btnToggleFilter.setOnClickListener {
            val visible = binding.cardFilterPanel.visibility == View.VISIBLE
            binding.cardFilterPanel.visibility = if (visible) View.GONE else View.VISIBLE
            binding.btnToggleFilter.text = if (visible) "▼" else "▲"
        }

        binding.etDateFrom.setOnClickListener { showDatePicker { vm.setDateFrom(it) } }
        binding.etDateTo.setOnClickListener { showDatePicker { vm.setDateTo(it) } }
        binding.chipDateFrom.setOnClickListener { showDatePicker { vm.setDateFrom(it) } }
        binding.chipDateTo.setOnClickListener { showDatePicker { vm.setDateTo(it) } }

        binding.etAmountMin.addTextChangedListener(simpleWatcher {
            vm.setAmountMin(it.toDoubleOrNull())
        })
        binding.etAmountMax.addTextChangedListener(simpleWatcher {
            vm.setAmountMax(it.toDoubleOrNull())
        })

        binding.btnClearFilters.setOnClickListener {
            vm.clearFilters()
            binding.etDateFrom.setText("")
            binding.etDateTo.setText("")
            binding.etAmountMin.setText("")
            binding.etAmountMax.setText("")
            binding.etSearch.setText("")
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    vm.searchedTransactions.collect { list ->
                        adapter.submitList(list)
                        binding.layoutEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    vm.dateFrom.collect { millis ->
                        val text = if (millis > 0L) DateFormatter.formatDate(millis) else ""
                        binding.etDateFrom.setText(text)
                        binding.chipDateFrom.text = if (millis > 0L) text else "Von"
                    }
                }
                launch {
                    vm.dateTo.collect { millis ->
                        val text = if (millis > 0L) DateFormatter.formatDate(millis) else ""
                        binding.etDateTo.setText(text)
                        binding.chipDateTo.text = if (millis > 0L) text else "Bis"
                    }
                }
                launch {
                    combine(vm.dateFrom, vm.dateTo, vm.amountMin, vm.amountMax) { df, dt, amin, amax ->
                        df > 0L || dt > 0L || amin != null || amax != null
                    }.collect { active ->
                        binding.layoutFilterBar.visibility = if (active) View.VISIBLE else View.GONE
                        binding.chipDateFrom.isChecked = vm.dateFrom.value > 0L
                        binding.chipDateTo.isChecked = vm.dateTo.value > 0L
                        binding.chipAmountMin.isChecked = vm.amountMin.value != null
                        binding.chipAmountMax.isChecked = vm.amountMax.value != null
                    }
                }
            }
        }
    }

    private fun showDatePicker(onSelected: (Long) -> Unit) {
        val cal = Calendar.getInstance()
        DatePickerDialog(requireContext(), { _, y, m, d ->
            cal.set(y, m, d, 0, 0, 0)
            onSelected(cal.timeInMillis)
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun simpleWatcher(onChange: (String) -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) { onChange(s?.toString() ?: "") }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
