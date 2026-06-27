package de.mybudgets.app.ui.transactions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import kotlin.math.abs
import de.mybudgets.app.R
import de.mybudgets.app.data.model.Category
import de.mybudgets.app.data.model.RecurringRule
import de.mybudgets.app.data.model.Transaction
import de.mybudgets.app.databinding.DialogRecurringPatternsBinding
import de.mybudgets.app.databinding.ItemRecurringPatternBinding
import de.mybudgets.app.util.AppLogger
import de.mybudgets.app.util.CurrencyFormatter
import de.mybudgets.app.util.DateFormatter
import de.mybudgets.app.util.RecurringPatternDetector

class RecurringPatternDialog : DialogFragment() {

    private var _binding: DialogRecurringPatternsBinding? = null
    private val binding get() = _binding!!
    private var patterns: List<RecurringPatternDetector.RecurringPattern> = emptyList()
    private var categories: List<Category> = emptyList()
    private var onApply: ((List<RecurringRule>) -> Unit)? = null
    private var onDismiss: (() -> Unit)? = null
    private val checkedState = mutableMapOf<Int, Boolean>()
    private val keywordOverrides = mutableMapOf<Int, String>()
    private val selectedCategoryId = mutableMapOf<Int, Long?>()
    private val expandedCategories = mutableMapOf<Int, MutableSet<Long>>()

    fun setOnDismissListener(listener: () -> Unit): RecurringPatternDialog {
        onDismiss = listener
        return this
    }

    fun setPatterns(patterns: List<RecurringPatternDetector.RecurringPattern>): RecurringPatternDialog {
        this.patterns = patterns
        return this
    }

    fun setCategories(categories: List<Category>): RecurringPatternDialog {
        this.categories = categories
        return this
    }

    fun setOnApplyListener(listener: (rules: List<RecurringRule>) -> Unit): RecurringPatternDialog {
        onApply = listener
        return this
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogRecurringPatternsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.setOnDismissListener { onDismiss?.invoke() }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            AppLogger.i("RecurringPatternDialog", "onViewCreated: patterns.size=${patterns.size}")
            dialog?.setTitle(getString(R.string.recurring_patterns_detected, patterns.size))

            patterns.indices.forEach { checkedState[it] = true }

            binding.tvEmpty.visibility = if (patterns.isEmpty()) View.VISIBLE else View.GONE
            binding.btnDiscard.setOnClickListener {
                AppLogger.i("RecurringPatternDialog", "btnDiscard clicked")
                dismiss()
            }
            binding.btnApply.setOnClickListener {
                AppLogger.i("RecurringPatternDialog", "btnApply clicked")
                applySelected()
            }
            buildPatternCards()
        } catch (e: Exception) {
            AppLogger.e("RecurringPatternDialog", "onViewCreated CRASH", e)
            try {
                android.widget.Toast.makeText(requireContext(), "Dialog-Initialisierung fehlgeschlagen: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                dismiss()
            } catch (_: Exception) {}
        }
    }

    private fun buildPatternCards() {
        try {
            AppLogger.i("RecurringPatternDialog", "buildPatternCards: START")
            for ((idx, p) in patterns.withIndex()) {
                try {
                    AppLogger.i("RecurringPatternDialog", "  Pattern $idx: ${p.suggestedDescription}, ${p.transactions.size} TX")
                    val card = ItemRecurringPatternBinding.inflate(layoutInflater, binding.containerPatterns, true)

                    if (p.transactions.isEmpty()) {
                        AppLogger.w("RecurringPatternDialog", "  ⚠️ Pattern $idx hat keine Transaktionen, skip")
                        continue
                    }

                    val tx = p.transactions.first()

                    card.cbPattern.isChecked = checkedState[idx] ?: true
                    card.cbPattern.setOnCheckedChangeListener { _, isChecked -> checkedState[idx] = isChecked }

                    card.tvPatternDescription.text = p.suggestedDescription ?: "Unbekannt"
                    card.tvPatternAmount.text = CurrencyFormatter.format(tx.amount, "EUR")
                    card.tvPatternInterval.text = p.intervalLabel ?: ""
                    card.tvPatternConfidence.text = "${(p.confidence * 100).toInt()}%"

                    val firstDate = p.transactions.firstOrNull()?.date
                    val lastDate = p.transactions.lastOrNull()?.date
                    if (firstDate != null && lastDate != null) {
                        card.tvPatternDateRange.text = getString(R.string.recurring_pattern_date_range,
                            DateFormatter.formatDate(firstDate),
                            DateFormatter.formatDate(lastDate))
                    } else {
                        card.tvPatternDateRange.text = "Zeitraum unbekannt"
                    }

                    card.tvTransactionCount.text = getString(R.string.recurring_pattern_tx_count, p.transactions.size)

                    card.etPatternKeyword.setText(p.suggestedDescription ?: "")
                    card.etPatternAmountFilter.setText(CurrencyFormatter.format(abs(tx.amount), "EUR"))

                    try {
                        addSuggestionChips(card.chipGroupSuggestions, p.transactions, card.etPatternKeyword, card.containerTxRows, p.transactions)
                    } catch (e: Exception) {
                        AppLogger.e("RecurringPatternDialog", "  addSuggestionChips fehlgeschlagen für Pattern $idx", e)
                    }

                    try {
                        addTransactionRows(card.containerTxRows, p.transactions)
                    } catch (e: Exception) {
                        AppLogger.e("RecurringPatternDialog", "  addTransactionRows fehlgeschlagen für Pattern $idx", e)
                    }

                    try {
                        buildCategoryList(card.categoryGroup, idx, p.transactions)
                    } catch (e: Exception) {
                        AppLogger.e("RecurringPatternDialog", "  buildCategoryList fehlgeschlagen für Pattern $idx", e)
                    }

                    card.tvPatternReason.text = p.reasoning ?: ""

                    card.ivExpand.setOnClickListener {
                        try {
                            toggleDetail(card.layoutDetail, card.ivExpand)
                        } catch (e: Exception) {
                            AppLogger.e("RecurringPatternDialog", "toggleDetail fehlgeschlagen", e)
                        }
                    }
                    card.root.setOnClickListener {
                        try {
                            toggleDetail(card.layoutDetail, card.ivExpand)
                        } catch (e: Exception) {
                            AppLogger.e("RecurringPatternDialog", "toggleDetail fehlgeschlagen", e)
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.e("RecurringPatternDialog", "  ❌ Pattern $idx konnte nicht gebaut werden", e)
                }
            }
            AppLogger.i("RecurringPatternDialog", "buildPatternCards: DONE")
        } catch (e: Exception) {
            AppLogger.e("RecurringPatternDialog", "buildPatternCards CRASH", e)
        }
    }

    private fun buildCategoryList(categoryGroup: RadioGroup, patternIdx: Int, transactions: List<Transaction>) {
        if (categories.isEmpty()) {
            categoryGroup.visibility = View.GONE
            return
        }

        val preSelected = transactions.map { it.categoryId }.distinct().singleOrNull()
        selectedCategoryId[patternIdx] = preSelected

        val perPatternExpanded = expandedCategories.getOrPut(patternIdx) { mutableSetOf() }

        fun rebuild() {
            categoryGroup.removeAllViews()
            val topLevel = categories.filter { it.parentCategoryId == null }.sortedBy { it.name }

            val currentSelected = selectedCategoryId[patternIdx]
            if (currentSelected != null && categories.none { it.id == currentSelected }) {
                selectedCategoryId[patternIdx] = null
            }

            fun addItems(cat: Category, indent: Int) {
                val hasChildren = categories.any { it.parentCategoryId == cat.id }
                val isExpanded = perPatternExpanded.contains(cat.id)

                val rb = RadioButton(requireContext()).apply {
                    layoutParams = ViewGroup.MarginLayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).also { it.marginStart = indent * 32 }
                    textSize = 13f

                    val prefix = if (hasChildren) {
                        if (isExpanded) "\u25BC " else "\u25B6 "
                    } else ""

                    text = "$prefix${cat.name}"
                    tag = cat.id
                    isChecked = cat.id == currentSelected

                    if (hasChildren) {
                        setOnClickListener {
                            if (isExpanded) perPatternExpanded.remove(cat.id)
                            else perPatternExpanded.add(cat.id)
                            rebuild()
                        }
                    } else {
                        setOnClickListener {
                            selectedCategoryId[patternIdx] = cat.id
                            rebuild()
                        }
                    }
                }
                categoryGroup.addView(rb)

                if (hasChildren && isExpanded) {
                    val children = categories
                        .filter { it.parentCategoryId == cat.id }
                        .sortedBy { it.name }
                    children.forEach { child -> addItems(child, indent + 1) }
                }
            }

            topLevel.forEach { addItems(it, 0) }
        }

        rebuild()
    }

    private fun addSuggestionChips(
        chipGroup: com.google.android.material.chip.ChipGroup,
        transactions: List<Transaction>,
        keywordInput: com.google.android.material.textfield.TextInputEditText,
        txContainer: LinearLayout,
        allTx: List<Transaction>
    ) {
        val wordCounts = mutableMapOf<String, Int>()
        for (tx in transactions) {
            for (word in (tx.description + " " + tx.note).split(Regex("[/\\s,;:]+"))) {
                val w = word.trim()
                if (w.length >= 3 && w.all { c -> c.isLetterOrDigit() || c == '-' }) {
                    wordCounts[w] = (wordCounts[w] ?: 0) + 1
                }
            }
        }
        val words = wordCounts.filter { it.value >= 2 }.keys.sorted()

        for (word in words) {
            val chip = com.google.android.material.chip.Chip(requireContext())
            chip.text = word
            chip.isCheckable = true
            chip.isChecked = word.equals(keywordInput.text?.toString(), ignoreCase = true)
            chip.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    keywordInput.setText(word)
                    filterTransactions(txContainer, allTx, word)
                } else {
                    keywordInput.setText("")
                    rebuildTransactionRows(txContainer, allTx)
                }
            }
            chipGroup.addView(chip)
        }
        chipGroup.visibility = if (words.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun filterTransactions(container: LinearLayout, allTx: List<Transaction>, keyword: String) {
        val filtered = allTx.filter { tx ->
            (tx.description + " " + tx.note).contains(keyword, ignoreCase = true)
        }
        rebuildTransactionRows(container, filtered)
    }

    private fun rebuildTransactionRows(container: LinearLayout, txList: List<Transaction>) {
        container.removeAllViews()
        addTransactionRows(container, txList)
    }

    private fun addTransactionRows(container: LinearLayout, transactions: List<Transaction>) {
        for (tx in transactions) {
            val row = layoutInflater.inflate(android.R.layout.simple_list_item_2, container, false) as View
            val text1 = row.findViewById<TextView>(android.R.id.text1)
            val text2 = row.findViewById<TextView>(android.R.id.text2)
            text1.text = "${DateFormatter.formatDate(tx.date)} · ${CurrencyFormatter.format(tx.amount)}"
            text2.text = tx.description.ifBlank { "Buchung" }
            container.addView(row)
        }
    }

    private fun toggleDetail(detail: LinearLayout, arrow: View) {
        val isVisible = detail.visibility == View.VISIBLE
        detail.visibility = if (isVisible) View.GONE else View.VISIBLE
        arrow.rotation = if (isVisible) 0f else 180f
    }

    private fun applySelected() {
        try {
            AppLogger.i("RecurringPatternDialog", "applySelected: START")
            val rules = mutableListOf<RecurringRule>()

            for ((idx, p) in patterns.withIndex()) {
                try {
                    if (checkedState[idx] != true) {
                        AppLogger.i("RecurringPatternDialog", "  Pattern $idx nicht ausgewählt, skip")
                        continue
                    }

                    if (idx >= binding.containerPatterns.childCount) {
                        AppLogger.w("RecurringPatternDialog", "  Pattern $idx hat kein Child-View, skip")
                        continue
                    }

                    val child = binding.containerPatterns.getChildAt(idx)
                    if (child == null) {
                        AppLogger.w("RecurringPatternDialog", "  Pattern $idx: Child-View ist null, skip")
                        continue
                    }

                    val card = ItemRecurringPatternBinding.bind(child)
                    val kw = card.etPatternKeyword.text?.toString()?.trim() ?: ""
                    if (kw.isEmpty()) {
                        AppLogger.w("RecurringPatternDialog", "  Pattern $idx: Keyword leer, skip")
                        continue
                    }

                    if (p.transactions.isEmpty()) {
                        AppLogger.w("RecurringPatternDialog", "  Pattern $idx: Keine Transaktionen, skip")
                        continue
                    }

                    val tx = p.transactions.first()
                    val iban = card.etPatternIban.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
                    val amountText = card.etPatternAmountFilter.text?.toString()?.trim()?.replace(",", ".")
                    val amountOverride = amountText?.toDoubleOrNull()
                    val toleranceText = card.etPatternTolerance.text?.toString()?.trim()?.replace(",", ".")
                    val toleranceOverride = toleranceText?.toDoubleOrNull()

                    val rule = RecurringRule(
                        name = kw,
                        matchKeyword = kw,
                        matchAmount = amountOverride ?: abs(tx.amount),
                        matchIban = iban,
                        matchAmountTolerance = toleranceOverride,
                        intervalDays = p.detectedIntervalDays,
                        categoryId = selectedCategoryId[idx],
                        accountId = tx.accountId
                    )
                    rules.add(rule)
                    AppLogger.i("RecurringPatternDialog", "  ✅ Pattern $idx → Rule erstellt: ${rule.name}")
                } catch (e: Exception) {
                    AppLogger.e("RecurringPatternDialog", "  ❌ Pattern $idx: Fehler beim Erstellen der Rule", e)
                }
            }

            AppLogger.i("RecurringPatternDialog", "applySelected: ${rules.size} Rules erstellt")
            if (rules.isNotEmpty()) {
                try {
                    onApply?.invoke(rules)
                } catch (e: Exception) {
                    AppLogger.e("RecurringPatternDialog", "onApply callback CRASH", e)
                    throw e
                }
            }
            dismiss()
        } catch (e: Exception) {
            AppLogger.e("RecurringPatternDialog", "applySelected CRASH", e)
            try {
                android.widget.Toast.makeText(requireContext(), "Fehler beim Anwenden: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            } catch (_: Exception) {}
            try {
                dismiss()
            } catch (_: Exception) {}
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "RecurringPatternDialog"

        fun newInstance(patterns: List<RecurringPatternDetector.RecurringPattern>): RecurringPatternDialog {
            return RecurringPatternDialog().setPatterns(patterns)
        }
    }
}
