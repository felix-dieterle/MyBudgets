package de.mybudgets.app.ui.transactions

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import com.google.android.material.chip.Chip
import de.mybudgets.app.R
import de.mybudgets.app.databinding.DialogPatternPickerBinding
import de.mybudgets.app.data.model.Category
import de.mybudgets.app.data.model.Transaction

class PatternPickerDialogFragment : DialogFragment() {

    private var _binding: DialogPatternPickerBinding? = null
    private val binding get() = _binding!!

    private var transaction: Transaction? = null
    private var allCategories: List<Category> = emptyList()
    private var expandedCategories = mutableSetOf<Long>()
    private var selectedCategoryId: Long? = null
    private var existingPatternValue: String? = null
    private var onPatternSelected: ((patternType: String?, patternValue: String?, categoryId: Long?, matchedName: String?, amountMin: Double?, amountMax: Double?, filterIncome: Boolean?) -> Unit)? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogPatternPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tx = transaction ?: run { dismiss(); return }

        setupIbanOption(tx)
        setupTextOption(tx)
        setupCategoryList()
        setupRadioBehavior()
        setupButtons()
    }

    private fun setupIbanOption(tx: Transaction) {
        val iban = extractIban(tx.note)
        if (iban != null) {
            binding.radioIban.isEnabled = true
            binding.tvIbanValue.text = maskIban(iban)
            binding.tvIbanValue.isVisible = true
            binding.tvIbanHint.text = "Alle Buchungen von diesem Empfänger"
            binding.tvIbanHint.isVisible = true
        } else {
            binding.radioIban.isEnabled = false
        }
    }

    private fun setupTextOption(tx: Transaction) {
        val sourceText = tx.originalDescription.ifBlank { tx.description }
        val keywords = extractKeywords(sourceText)

        val savedKeywords = existingPatternValue?.split("|")?.map { it.trim().lowercase() }.orEmpty()

        if (keywords.isNotEmpty()) {
            binding.chipGroupKeywords.removeAllViews()
            keywords.forEach { keyword ->
                val chip = Chip(requireContext()).apply {
                    text = keyword
                    isCheckable = true
                    isChecked = keyword.lowercase() in savedKeywords
                }
                binding.chipGroupKeywords.addView(chip)
            }
        } else {
            binding.radioText.isEnabled = false
        }
    }

    private fun setupCategoryList() {
        if (allCategories.isEmpty()) {
            binding.categoryGroup.visibility = View.GONE
            return
        }
        rebuildCategoryList()
    }

    private fun rebuildCategoryList() {
        binding.categoryGroup.removeAllViews()
        selectedCategoryId?.let { prevId ->
            if (allCategories.none { it.id == prevId }) selectedCategoryId = null
        }

        val topLevel = allCategories.filter { it.parentCategoryId == null }.sortedBy { it.name }

        fun addItems(cat: Category, indent: Int) {
            val hasChildren = allCategories.any { it.parentCategoryId == cat.id }
            val isExpanded = expandedCategories.contains(cat.id)

            val rb = RadioButton(requireContext()).apply {
                layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { it.marginStart = indent * 32 }

                val prefix = if (hasChildren) {
                    if (isExpanded) "\u25BC " else "\u25B6 "
                } else ""

                text = "$prefix${cat.name}"
                tag = cat.id

                if (hasChildren) {
                    setOnClickListener {
                        if (isExpanded) expandedCategories.remove(cat.id)
                        else expandedCategories.add(cat.id)
                        rebuildCategoryList()
                    }
                } else {
                    setOnClickListener {
                        selectedCategoryId = cat.id
                        binding.btnSave.isEnabled = true
                        updateCategorySelection()
                    }
                }
            }
            binding.categoryGroup.addView(rb)

            if (hasChildren && isExpanded) {
                val children = allCategories
                    .filter { it.parentCategoryId == cat.id }
                    .sortedBy { it.name }
                children.forEach { child -> addItems(child, indent + 1) }
            }
        }

        topLevel.forEach { addItems(it, 0) }
        updateCategorySelection()
    }

    private fun updateCategorySelection() {
        for (i in 0 until binding.categoryGroup.childCount) {
            val rb = binding.categoryGroup.getChildAt(i) as? RadioButton ?: continue
            rb.isChecked = rb.tag == selectedCategoryId
        }
    }

    private fun setupRadioBehavior() {
        binding.radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val showKeywords = checkedId == R.id.radioText
            binding.keywordsSection.visibility = if (showKeywords) View.VISIBLE else View.GONE
        }
    }

    private fun setupButtons() {
        binding.btnCancel.setOnClickListener { dismiss() }

        binding.btnSave.setOnClickListener {
            val catId = selectedCategoryId ?: return@setOnClickListener

            val tx = transaction ?: return@setOnClickListener

            val patternType: String?
            val patternValue: String?

            when {
                binding.radioIban.isChecked -> {
                    val iban = extractIban(tx.note)
                    patternType = if (iban != null) "IBAN" else null
                    patternValue = iban
                }
                binding.radioText.isChecked -> {
                    val keywords = getSelectedKeywords()
                    if (keywords.isNotEmpty()) {
                        patternType = "TEXT"
                        patternValue = keywords.joinToString("|")
                    } else {
                        patternType = null
                        patternValue = null
                    }
                }
                else -> {
                    patternType = null
                    patternValue = null
                }
            }

            val matchedName = binding.etMatchedName.text?.toString()?.trim()?.takeIf { it.isNotBlank() }

            val amountMin = binding.etAmountMin.text?.toString()?.toDoubleOrNull()
            val amountMax = binding.etAmountMax.text?.toString()?.toDoubleOrNull()
            val filterIncome = when (binding.chipGroupSign.checkedChipId) {
                R.id.chipSignIncome -> true
                R.id.chipSignExpense -> false
                else -> null
            }

            onPatternSelected?.invoke(patternType, patternValue, catId, matchedName, amountMin, amountMax, filterIncome)
            dismiss()
        }
    }

    private fun getSelectedKeywords(): List<String> {
        val selected = mutableListOf<String>()
        for (i in 0 until binding.chipGroupKeywords.childCount) {
            val chip = binding.chipGroupKeywords.getChildAt(i) as? Chip
            if (chip?.isChecked == true) {
                selected.add(chip.text.toString())
            }
        }
        return selected
    }

    private fun extractIban(note: String): String? {
        val ibanRegex = """IBAN:\s*([A-Z]{2}\d{2}[A-Z0-9]+)""".toRegex()
        return ibanRegex.find(note)?.groupValues?.get(1)
    }

    private fun maskIban(iban: String): String {
        if (iban.length < 8) return iban
        return "${iban.take(4)}****${iban.takeLast(4)}"
    }

    internal fun extractKeywords(description: String): List<String> {
        val stopwords = setOf(
            "sagt", "danke", "ihr", "einkauf", "bei", "fuer", "fur",
            "von", "an", "mit", "der", "die", "das", "den", "dem"
        )

        return description
            .replace(".", " ")
            .split(" ", "-", "/")
            .map { it.trim().lowercase() }
            .filter { it.length >= 3 && it !in stopwords }
            .filter { it.any { c -> c.isLetterOrDigit() } }
            .distinct()
    }

    fun setTransaction(tx: Transaction): PatternPickerDialogFragment {
        this.transaction = tx
        return this
    }

    fun setCategories(categories: List<Category>): PatternPickerDialogFragment {
        this.allCategories = categories
        return this
    }

    fun setExistingPatternValue(value: String?): PatternPickerDialogFragment {
        this.existingPatternValue = value
        return this
    }

    fun setOnPatternSelectedListener(listener: (patternType: String?, patternValue: String?, categoryId: Long?, matchedName: String?, amountMin: Double?, amountMax: Double?, filterIncome: Boolean?) -> Unit): PatternPickerDialogFragment {
        this.onPatternSelected = listener
        return this
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(tx: Transaction, categories: List<Category>): PatternPickerDialogFragment {
            return PatternPickerDialogFragment()
                .setTransaction(tx)
                .setCategories(categories)
        }
    }
}
