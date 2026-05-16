package de.mybudgets.app.ui.transactions

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import de.mybudgets.app.R
import de.mybudgets.app.data.model.CategoryPattern
import de.mybudgets.app.data.model.Transaction
import de.mybudgets.app.data.repository.CategoryPatternRepository
import de.mybudgets.app.data.repository.CategoryRepository
import de.mybudgets.app.databinding.FragmentTransactionDetailBinding
import de.mybudgets.app.util.CurrencyFormatter
import de.mybudgets.app.util.DateFormatter
import de.mybudgets.app.viewmodel.TransactionViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TransactionDetailFragment : Fragment() {

    private var _binding: FragmentTransactionDetailBinding? = null
    private val binding get() = _binding!!
    private val vm: TransactionViewModel by viewModels()
    private var transactionId: Long = 0L
    private var currentTx: Transaction? = null

    @Inject lateinit var categoryRepository: CategoryRepository
    @Inject lateinit var categoryPatternRepository: CategoryPatternRepository

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentTransactionDetailBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        transactionId = arguments?.getLong("transactionId") ?: 0L
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.transactions.collect { list ->
                    list.find { it.id == transactionId }?.let { show(it) }
                }
            }
        }
        binding.btnSavePattern.setOnClickListener { showPatternDialog() }
    }

    private fun show(tx: Transaction) {
        currentTx = tx
        binding.tvTxDescription.text = tx.description.ifBlank { "Buchung" }
        binding.tvTxAmount.text      = CurrencyFormatter.format(tx.amount)
        binding.tvTxDate.text        = DateFormatter.formatDate(tx.date)
        binding.tvTxType.text        = tx.type.name
        binding.tvTxNote.text        = tx.note.ifBlank { "—" }
        binding.tvTxCategory.text    = if (tx.categoryId != null) "Kategorie: #${tx.categoryId}" else ""
    }

    private fun showPatternDialog() {
        val tx = currentTx ?: return
        PatternPickerDialogFragment.newInstance(tx)
            .setOnPatternSelectedListener { type, value ->
                if (type != null && value != null) {
                    showCategoryPicker(type, value)
                }
            }
            .show(childFragmentManager, "PatternPicker")
    }

    private fun showCategoryPicker(patternType: String, patternValue: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val categories = categoryRepository.getTopLevel()
            val names = categories.map { c -> c.name }.toTypedArray()
            if (names.isEmpty()) {
                MaterialAlertDialogBuilder(requireActivity())
                    .setTitle("Keine Kategorien")
                    .setMessage("Bitte zuerst Kategorien anlegen")
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                return@launch
            }
            MaterialAlertDialogBuilder(requireActivity())
                .setTitle("Kategorie wählen")
                .setItems(names) { _, which ->
                    val cat = categories[which]
                    viewLifecycleOwner.lifecycleScope.launch {
                        categoryPatternRepository.save(
                            CategoryPattern(
                                categoryId = cat.id,
                                patternType = patternType,
                                patternValue = patternValue
                            )
                        )
                        MaterialAlertDialogBuilder(requireActivity())
                            .setTitle("Muster gespeichert")
                            .setMessage("Zukünftige Buchungen mit \"$patternValue\" werden automatisch als \"${cat.name}\" kategorisiert.")
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
