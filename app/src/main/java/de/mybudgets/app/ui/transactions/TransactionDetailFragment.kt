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
import de.mybudgets.app.viewmodel.CategoryViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class TransactionDetailFragment : Fragment() {

    private var _binding: FragmentTransactionDetailBinding? = null
    private val binding get() = _binding!!
    private val vm: TransactionViewModel by viewModels()
    private val categoryVm: CategoryViewModel by viewModels()
    private var transactionId: Long = 0L
    private var currentTx: Transaction? = null
    private var allCategoriesCache: List<de.mybudgets.app.data.model.Category> = emptyList()

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
                launch {
                    // Cache all categories for bulk update dialog
                    allCategoriesCache = try {
                        categoryRepository.observeAll().first()
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
                launch {
                    vm.transactions.collect { list ->
                        list.find { it.id == transactionId }?.let { show(it) }
                    }
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
        viewLifecycleOwner.lifecycleScope.launch {
            val cats = try {
                categoryRepository.observeAll().first()
            } catch (e: Exception) {
                allCategoriesCache
            }
            if (cats.isEmpty()) {
                MaterialAlertDialogBuilder(requireActivity())
                    .setTitle("Keine Kategorien")
                    .setMessage("Bitte zuerst Kategorien anlegen")
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                return@launch
            }
            val existingPatternValue = if (tx.categoryId != null) {
                categoryPatternRepository.observeByCategory(tx.categoryId).first()
                    .firstOrNull { it.patternType == "TEXT" }?.patternValue
            } else null

            PatternPickerDialogFragment.newInstance(tx, cats)
                .setExistingPatternValue(existingPatternValue)
                .setOnPatternSelectedListener { type, value, categoryId, matchedName ->
                    if (categoryId != null) {
                        savePatternAndCategorize(type, value, categoryId, matchedName)
                    }
                }
                .show(childFragmentManager, "PatternPicker")
        }
    }

    private fun savePatternAndCategorize(patternType: String?, patternValue: String?, categoryId: Long, matchedName: String? = null) {
        val tx = currentTx ?: return
        val selectedCat = allCategoriesCache.find { it.id == categoryId } ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            if (patternType != null && patternValue != null) {
                categoryPatternRepository.save(
                    CategoryPattern(
                        categoryId = selectedCat.id,
                        patternType = patternType,
                        patternValue = patternValue,
                        matchedName = matchedName ?: ""
                    )
                )
            }

            val patchedDescription = matchedName ?: tx.description
            val origDesc = if (matchedName != null && tx.originalDescription.isBlank()) tx.description else tx.originalDescription
            vm.save(tx.copy(
                categoryId = selectedCat.id,
                description = patchedDescription,
                originalDescription = origDesc
            ))

            if (patternType != null && patternValue != null) {
                val allTxs = vm.transactions.value
                val matchingTxs = findMatchingTransactions(allTxs, patternType, patternValue)
                    .filter { it.id != tx.id }

                if (matchingTxs.isNotEmpty()) {
                    showBulkUpdateDialog(matchingTxs, selectedCat, patternValue, matchedName)
                } else {
                    MaterialAlertDialogBuilder(requireActivity())
                        .setTitle("Muster gespeichert")
                        .setMessage("Zukünftige Buchungen mit \"$patternValue\" werden automatisch als \"${selectedCat.name}\" kategorisiert.\n\nDiese Buchung wurde ebenfalls kategorisiert.")
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            } else {
                MaterialAlertDialogBuilder(requireActivity())
                    .setTitle("Kategorie zugewiesen")
                    .setMessage("Buchung wurde als \"${selectedCat.name}\" kategorisiert.")
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }
    }

    
    private fun findMatchingTransactions(
        allTxs: List<Transaction>,
        patternType: String,
        patternValue: String
    ): List<Transaction> {
        return allTxs.filter { tx ->
            de.mybudgets.app.util.PatternMatcher.matches(patternType, patternValue, tx.description, tx.note)
        }
    }
    
    private fun showBulkUpdateDialog(
        matchingTxs: List<Transaction>,
        selectedCat: de.mybudgets.app.data.model.Category,
        patternValue: String,
        matchedName: String? = null
    ) {
        val applyName: (Transaction) -> Transaction = { tx ->
            tx.copy(
                categoryId = selectedCat.id,
                description = matchedName ?: tx.description,
                originalDescription = if (matchedName != null && tx.originalDescription.isBlank()) tx.description else tx.originalDescription
            )
        }
        val uncategorized = matchingTxs.filter { it.categoryId == null }
        val categorized = matchingTxs.filter { it.categoryId != null }
        val withSameCategory = categorized.filter { it.categoryId == selectedCat.id }
        val withDifferentCategory = categorized.filter { it.categoryId != selectedCat.id }
        
        // If all categorized have the same category as selected, no conflict
        if (withDifferentCategory.isEmpty()) {
            // Just update uncategorized, no dialog needed
            if (uncategorized.isNotEmpty()) {
                viewLifecycleOwner.lifecycleScope.launch {
                    uncategorized.forEach { tx ->
                        vm.save(applyName(tx))
                    }
                    MaterialAlertDialogBuilder(requireActivity())
                        .setTitle("✅ Fertig")
                        .setMessage("${uncategorized.size} Buchungen wurden kategorisiert als \"${selectedCat.name}\"\n${withSameCategory.size} waren bereits korrekt kategorisiert")
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            } else {
                MaterialAlertDialogBuilder(requireActivity())
                    .setTitle("✅ Fertig")
                    .setMessage("Alle ${matchingTxs.size} Buchungen sind bereits als \"${selectedCat.name}\" kategorisiert")
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
            return
        }
        
        // Build message showing conflicts with category names
        val conflictCategories = withDifferentCategory
            .groupBy { it.categoryId }
            .mapValues { (catId, txs) ->
                val catName = allCategoriesCache.find { it.id == catId }?.name ?: "#$catId"
                Pair(catName, txs)
            }
        
        val conflictText = conflictCategories.entries
            .sortedBy { it.value.second.size }
            .joinToString("\n") { (_, pair) ->
                val (catName, txs) = pair
                "  • $catName: ${txs.size} Buchung(en)"
            }
        
        val message = buildString {
            append("Es gibt ${matchingTxs.size} weitere Buchungen mit dem gleichen Muster:\n\n")
            append("✓ Uncategorized: ${uncategorized.size}\n")
            append("✓ Already \"${selectedCat.name}\": ${withSameCategory.size}\n")
            append("⚠ Unterschiedliche Kategorie: ${withDifferentCategory.size}\n")
            if (conflictText.isNotEmpty()) {
                append("\n$conflictText\n")
            }
            append("\nWas möchtest du mit den ${withDifferentCategory.size} Konflikten tun?")
        }
        
        MaterialAlertDialogBuilder(requireActivity())
            .setTitle("Muster erkannt")
            .setMessage(message)
            .setPositiveButton("Update Conflicts") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    uncategorized.forEach { tx ->
                        vm.save(applyName(tx))
                    }
                    withDifferentCategory.forEach { tx ->
                        vm.save(applyName(tx))
                    }
                    MaterialAlertDialogBuilder(requireActivity())
                        .setTitle("✅ Fertig")
                        .setMessage("${uncategorized.size} uncategorized + ${withDifferentCategory.size} Konflikt-Buchungen wurden kategorisiert als \"${selectedCat.name}\"\n${withSameCategory.size} waren bereits korrekt kategorisiert")
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
            .setNegativeButton("Keep Existing") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    uncategorized.forEach { tx ->
                        vm.save(applyName(tx))
                    }
                    MaterialAlertDialogBuilder(requireActivity())
                        .setTitle("✅ Fertig")
                        .setMessage("${uncategorized.size} uncategorized Buchungen wurden kategorisiert als \"${selectedCat.name}\"\n${withDifferentCategory.size} Konflikt-Buchungen wurden nicht verändert")
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
