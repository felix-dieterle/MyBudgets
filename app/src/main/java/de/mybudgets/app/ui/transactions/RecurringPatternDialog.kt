package de.mybudgets.app.ui.transactions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.abs
import kotlinx.coroutines.launch
import de.mybudgets.app.R
import de.mybudgets.app.data.model.Category
import de.mybudgets.app.data.model.PatternEvaluation
import de.mybudgets.app.data.model.RecurringRule
import de.mybudgets.app.data.model.Transaction
import de.mybudgets.app.data.repository.PatternEvaluationRepository
import de.mybudgets.app.data.repository.PatternOverrideRepository
import de.mybudgets.app.databinding.DialogRecurringPatternsBinding
import de.mybudgets.app.databinding.ItemRecurringPatternBinding
import de.mybudgets.app.util.AppLogger
import de.mybudgets.app.util.CurrencyFormatter
import de.mybudgets.app.util.DateFormatter
import de.mybudgets.app.util.PatternEvaluationSuggestion
import de.mybudgets.app.util.RecurringPatternDetector
import javax.inject.Inject

@AndroidEntryPoint
class RecurringPatternDialog : DialogFragment() {

    @Inject lateinit var evaluationRepository: PatternEvaluationRepository
    @Inject lateinit var overrideRepository: PatternOverrideRepository
    private val cardBindings = mutableMapOf<Int, ItemRecurringPatternBinding>()
    private val storedEvaluations = mutableMapOf<String, PatternEvaluation>()

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
            loadStoredEvaluations()
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
                    cardBindings[idx] = card

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
                        AppLogger.e("RecurringPatternDialog", "  buildCategoryList fehlgeschlagen für $idx", e)
                    }

                    card.btnApplyOverride.setOnClickListener { confirmApplyOverride(idx, card) }

                    card.tvPatternReason.text = p.reasoning ?: ""

                    applySuggestion(card, p)

                    val toggle = {
                        try {
                            toggleDetail(card.layoutDetail, card.ivExpand)
                        } catch (e: Exception) {
                            AppLogger.e("RecurringPatternDialog", "toggleDetail fehlgeschlagen", e)
                        }
                    }
                    card.ivExpand.setOnClickListener { toggle() }
                    card.layoutHeader.setOnClickListener { toggle() }

                    card.rbImportance.setOnRatingBarChangeListener { _, value, fromUser ->
                        if (fromUser) {
                            card.tvEvalSource.text = "Eigene Bewertung"
                            refreshRatingUi(card)
                            Toast.makeText(requireContext(), "Wichtigkeit: ${value.toInt()}/5", Toast.LENGTH_SHORT).show()
                        }
                    }
                    card.rbCuttability.setOnRatingBarChangeListener { _, value, fromUser ->
                        if (fromUser) {
                            card.tvEvalSource.text = "Eigene Bewertung"
                            refreshRatingUi(card)
                            Toast.makeText(requireContext(), "Kürzbarkeit: ${value.toInt()}/5", Toast.LENGTH_SHORT).show()
                        }
                    }
                    refreshRatingUi(card)
                } catch (e: Exception) {
                    AppLogger.e("RecurringPatternDialog", "  ❌ Pattern $idx konnte nicht gebaut werden", e)
                }
            }
            AppLogger.i("RecurringPatternDialog", "buildPatternCards: DONE")
        } catch (e: Exception) {
            AppLogger.e("RecurringPatternDialog", "buildPatternCards CRASH", e)
        }
    }

    private fun applySuggestion(card: ItemRecurringPatternBinding, p: RecurringPatternDetector.RecurringPattern) {
        val suggestion = PatternEvaluationSuggestion.suggest(p.transactions.size, p.confidence, p.detectedIntervalDays)
        card.rbImportance.rating = suggestion.importance.toFloat()
        card.rbCuttability.rating = suggestion.cuttability.toFloat()
        card.tvEvalSource.text = "Vorschlag (matcher ${PatternEvaluationSuggestion.MATCHER_VERSION}) - anpassbar"
        refreshRatingUi(card)
    }

    private fun refreshRatingUi(card: ItemRecurringPatternBinding) {
        val w = card.rbImportance.rating.toInt()
        val k = card.rbCuttability.rating.toInt()
        card.tvImportanceValue.text = "Wichtigkeit: $w/5"
        card.tvCuttabilityValue.text = "Kürzbarkeit: $k/5"
        card.tvRatingSummary.text = "Bewertung  W $w/5 · K $k/5"
    }

    private fun loadStoredEvaluations() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val all = evaluationRepository.getAll(PatternEvaluationSuggestion.MATCHER_VERSION)
                storedEvaluations.clear()
                all.forEach { storedEvaluations[it.patternKey] = it }
                applyStoredEvaluations()
            } catch (e: Exception) {
                AppLogger.e("RecurringPatternDialog", "loadStoredEvaluations CRASH", e)
            }
        }
    }

    private fun applyStoredEvaluations() {
        for ((idx, p) in patterns.withIndex()) {
            val card = cardBindings[idx] ?: continue
            val key = PatternEvaluationSuggestion.keyForPattern(p)
            val stored = storedEvaluations[key] ?: continue
            if (stored.importance > 0) card.rbImportance.rating = stored.importance.toFloat()
            if (stored.cuttability > 0) card.rbCuttability.rating = stored.cuttability.toFloat()
            if (stored.note.isNotBlank()) card.etEvalNote.setText(stored.note)
            card.tvEvalSource.text = "Bewertung gespeichert"
            refreshRatingUi(card)
        }
    }

    private fun persistEvaluation(idx: Int, card: ItemRecurringPatternBinding, p: RecurringPatternDetector.RecurringPattern) {
        try {
            val key = PatternEvaluationSuggestion.keyForPattern(p)
            if (key.isBlank()) return
            val existing = storedEvaluations[key]
            val evaluation = PatternEvaluation(
                id = existing?.id ?: 0,
                patternKey = key,
                matcherVersion = PatternEvaluationSuggestion.MATCHER_VERSION,
                importance = card.rbImportance.rating.toInt(),
                cuttability = card.rbCuttability.rating.toInt(),
                note = card.etEvalNote.text?.toString()?.trim() ?: "",
                categoryOverrideId = selectedCategoryId[idx],
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            evaluationRepository.saveAsync(evaluation)
        } catch (e: Exception) {
            AppLogger.e("RecurringPatternDialog", "persistEvaluation CRASH für $idx", e)
        }
    }

    /**
     * Wendet den gewaehlten Kategorie-Override auf alle passenden Buchungen an (US-004).
     * Zuerst Vorschau, dann explizite Bestaetigung - geschrieben wird ausschliesslich auf
     * Buchungen ohne Kategorie.
     */
    private fun confirmApplyOverride(idx: Int, card: ItemRecurringPatternBinding) {
        val p = patterns.getOrNull(idx) ?: return
        val categoryId = selectedCategoryId[idx]
        val categoryName = categories.firstOrNull { it.id == categoryId }?.name
        if (categoryId == null || categoryName == null) {
            Toast.makeText(requireContext(), getString(R.string.pattern_apply_override_no_category), Toast.LENGTH_SHORT).show()
            return
        }
        val key = PatternEvaluationSuggestion.keyForPattern(p)
        if (key.isBlank()) {
            Toast.makeText(requireContext(), getString(R.string.pattern_apply_override_no_key), Toast.LENGTH_SHORT).show()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val preview = try {
                overrideRepository.preview(key)
            } catch (e: Exception) {
                AppLogger.e("RecurringPatternDialog", "confirmApplyOverride preview CRASH", e)
                null
            }
            if (preview == null || preview.uncategorized == 0) {
                Toast.makeText(requireContext(), getString(R.string.pattern_apply_override_none), Toast.LENGTH_SHORT).show()
                return@launch
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.pattern_apply_override_title))
                .setMessage(
                    getString(
                        R.string.pattern_apply_override_message,
                        preview.matched, preview.uncategorized, preview.alreadyCategorized, categoryName
                    )
                )
                .setPositiveButton(R.string.pattern_apply_override_confirm) { _, _ ->
                    applyOverrideNow(idx, card, p, key, categoryId)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun applyOverrideNow(
        idx: Int,
        card: ItemRecurringPatternBinding,
        p: RecurringPatternDetector.RecurringPattern,
        key: String,
        categoryId: Long
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = overrideRepository.applyToMatching(key, categoryId)
                persistEvaluation(idx, card, p)
                Toast.makeText(
                    requireContext(),
                    getString(R.string.pattern_apply_override_done, result.applied, result.skipped),
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                AppLogger.e("RecurringPatternDialog", "applyOverrideNow CRASH für $idx", e)
                Toast.makeText(requireContext(), "Fehler: ${e.message}", Toast.LENGTH_LONG).show()
            }
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
                    persistEvaluation(idx, card, p)
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
        cardBindings.clear()
        storedEvaluations.clear()
        _binding = null
    }

    companion object {
        const val TAG = "RecurringPatternDialog"

        fun newInstance(patterns: List<RecurringPatternDetector.RecurringPattern>): RecurringPatternDialog {
            return RecurringPatternDialog().setPatterns(patterns)
        }
    }
}
