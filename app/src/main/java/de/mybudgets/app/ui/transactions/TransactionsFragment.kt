package de.mybudgets.app.ui.transactions

import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import de.mybudgets.app.R
import de.mybudgets.app.data.repository.CategoryRepository
import de.mybudgets.app.databinding.FragmentTransactionsBinding
import de.mybudgets.app.util.DateFormatter
import de.mybudgets.app.data.repository.PatternOverrideRepository
import de.mybudgets.app.viewmodel.TransactionViewModel
import de.mybudgets.app.worker.PREF_PENDING_OVERRIDE_RESETS
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@AndroidEntryPoint
class TransactionsFragment : Fragment() {

    private var _binding: FragmentTransactionsBinding? = null
    private val binding get() = _binding!!
    private val vm: TransactionViewModel by viewModels()
    private lateinit var adapter: TransactionAdapter

    @Inject lateinit var categoryRepository: CategoryRepository
    @Inject lateinit var patternOverrideRepository: PatternOverrideRepository

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

        binding.btnExportCsv.setOnClickListener {
            try {
                val csv = vm.exportFilteredAsCsv()
                // Ueber EXTRA_TEXT stuerzt der Export bei ~1 MB ab (TransactionTooLargeException).
                // Bei 2322 Buchungen ist die CSV gut 300 KB gross, deshalb wie der Log-Export
                // ueber eine Cache-Datei plus FileProvider teilen (05.10.2026).
                val csvFile = java.io.File(requireContext().cacheDir, "mybudgets-transactions.csv")
                csvFile.writeText(csv)
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.fileprovider",
                    csvFile
                )
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    putExtra(android.content.Intent.EXTRA_SUBJECT, "MyBudgets Export")
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(android.content.Intent.createChooser(intent, "CSV exportieren"))
            } catch (e: Exception) {
                com.google.android.material.snackbar.Snackbar.make(
                    requireView(),
                    "CSV-Export fehlgeschlagen: ${e.message}",
                    com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                ).show()
            }
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

        binding.btnSelectCategories.setOnClickListener {
            showCategoryFilterDialog()
        }

        binding.chipUncategorized.setOnCheckedChangeListener { _, isChecked ->
            vm.setShowUncategorizedOnly(isChecked)
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

        showPendingOverrideResetsIfAny()
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

    private fun showCategoryFilterDialog() {
        viewLifecycleOwner.lifecycleScope.launch {
            val allCategories = try {
                categoryRepository.observeAll().first()
            } catch (e: Exception) {
                emptyList()
            }

            if (allCategories.isEmpty()) {
                return@launch
            }

            CategoryFilterDialogFragment.newInstance(allCategories, vm.selectedCategoryIds.value)
                .apply {
                    setOnSaveListener { selectedIds ->
                        vm.setSelectedCategories(selectedIds)
                        binding.btnSelectCategories.text = "${selectedIds.size} gewählt"
                    }
                }
                .show(childFragmentManager, "CategoryFilter")
        }
    }

    /**
     * Ruecknahme (US-004): Wurde ein im Dashboard gesetzter Kategorie-Override dort wieder
     * entfernt, hat der Sync das nur vorgemerkt. Hier fragen wir nach - zurueckgesetzt wird
     * ausschliesslich nach Bestaetigung.
     */
    private fun showPendingOverrideResetsIfAny() {
        val prefs = requireContext().getSharedPreferences("mybudgets_prefs", Context.MODE_PRIVATE)
        val pending = prefs.getStringSet(PREF_PENDING_OVERRIDE_RESETS, emptySet())?.sorted().orEmpty()
        if (pending.isEmpty()) return
        viewLifecycleOwner.lifecycleScope.launch {
            val total = pending.sumOf { patternOverrideRepository.countApplied(it) }
            if (total == 0) {
                prefs.edit().remove(PREF_PENDING_OVERRIDE_RESETS).apply()
                return@launch
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.pattern_override_removed_title)
                .setMessage(getString(R.string.pattern_override_removed_message, total, pending.size))
                .setPositiveButton(R.string.pattern_override_removed_confirm) { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        pending.forEach { patternOverrideRepository.reset(it) }
                        prefs.edit().remove(PREF_PENDING_OVERRIDE_RESETS).apply()
                    }
                }
                .setNegativeButton(R.string.pattern_override_removed_keep) { _, _ ->
                    prefs.edit().remove(PREF_PENDING_OVERRIDE_RESETS).apply()
                }
                .show()
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
