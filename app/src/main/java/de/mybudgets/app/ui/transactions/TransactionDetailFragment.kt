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
            // Get all categories - use Flow.first() to get current value
            val allCategories = try {
                categoryRepository.observeAll().first()
            } catch (e: Exception) {
                emptyList()
            }
            
            if (allCategories.isEmpty()) {
                MaterialAlertDialogBuilder(requireActivity())
                    .setTitle("Keine Kategorien")
                    .setMessage("Bitte zuerst Kategorien anlegen")
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                return@launch
            }
            
            // Build hierarchical list with unfolding
            val expanded = mutableSetOf<Long>() // Track expanded L1 categories
            
            fun rebuildAndShowDialog() {
                val displayList = buildHierarchicalList(allCategories, expanded)
                val names = displayList.map { it.first }.toTypedArray()
                val categoryIds = displayList.map { it.second }
                
                MaterialAlertDialogBuilder(requireActivity())
                    .setTitle("Kategorie wählen")
                    .setItems(names) { _, which ->
                        val catId = categoryIds[which]
                        
                        // Check if this is a collapsible L1 category
                        val cat = allCategories.find { it.id == catId }
                        if (cat?.level == 1 && allCategories.any { it.parentCategoryId == cat.id }) {
                            // Toggle expand and show dialog again
                            if (expanded.contains(catId)) {
                                expanded.remove(catId)
                            } else {
                                expanded.add(catId)
                            }
                            rebuildAndShowDialog()
                        } else {
                            // User selected a leaf or L2/L3 category - save it
                            val selectedCat = allCategories.find { it.id == catId } ?: return@setItems
                            val tx = currentTx ?: return@setItems
                            viewLifecycleOwner.lifecycleScope.launch {
                                // 1. Save pattern for future transactions
                                categoryPatternRepository.save(
                                    CategoryPattern(
                                        categoryId = selectedCat.id,
                                        patternType = patternType,
                                        patternValue = patternValue
                                    )
                                )
                                
                                // 2. Update current transaction with category
                                vm.save(tx.copy(categoryId = selectedCat.id))
                                
                                // 3. Find all other TXs matching this pattern and ask user if they want to update them
                                val allTxs = vm.transactions.value
                                val matchingTxs = findMatchingTransactions(allTxs, patternType, patternValue)
                                    .filter { it.id != tx.id } // Exclude current TX
                                
                                if (matchingTxs.isNotEmpty()) {
                                    showBulkUpdateDialog(matchingTxs, selectedCat, patternValue)
                                } else {
                                    MaterialAlertDialogBuilder(requireActivity())
                                        .setTitle("Muster gespeichert")
                                        .setMessage("Zukünftige Buchungen mit \"$patternValue\" werden automatisch als \"${selectedCat.name}\" kategorisiert.\n\nDiese Buchung wurde ebenfalls kategorisiert.")
                                        .setPositiveButton(android.R.string.ok, null)
                                        .show()
                                }
                            }
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            
            rebuildAndShowDialog()
        }
    }
    
    private fun buildHierarchicalList(
        allCategories: List<de.mybudgets.app.data.model.Category>,
        expanded: Set<Long>
    ): List<Pair<String, Long>> {
        val result = mutableListOf<Pair<String, Long>>()
        
        // Get top-level categories
        val topLevel = allCategories.filter { it.parentCategoryId == null }.sortedBy { it.name }
        
        fun addWithChildren(cat: de.mybudgets.app.data.model.Category, indent: Int) {
            val prefix = "  ".repeat(indent)
            val hasChildren = allCategories.any { it.parentCategoryId == cat.id }
            val arrow = if (cat.level == 1 && hasChildren) {
                if (expanded.contains(cat.id)) "▼ " else "► "
            } else ""
            result.add(Pair("$arrow$prefix${cat.name}", cat.id))
            
            // Add children if expanded (or always for non-L1)
            if (cat.level == 1) {
                if (expanded.contains(cat.id)) {
                    val children = allCategories
                        .filter { it.parentCategoryId == cat.id }
                        .sortedBy { it.name }
                    children.forEach { child ->
                        addWithChildren(child, indent + 1)
                    }
                }
            } else {
                val children = allCategories
                    .filter { it.parentCategoryId == cat.id }
                    .sortedBy { it.name }
                children.forEach { child ->
                    addWithChildren(child, indent + 1)
                }
            }
        }
        
        topLevel.forEach { addWithChildren(it, 0) }
        return result
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
        patternValue: String
    ) {
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
                        vm.save(tx.copy(categoryId = selectedCat.id))
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
                        vm.save(tx.copy(categoryId = selectedCat.id))
                    }
                    withDifferentCategory.forEach { tx ->
                        vm.save(tx.copy(categoryId = selectedCat.id))
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
                        vm.save(tx.copy(categoryId = selectedCat.id))
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
