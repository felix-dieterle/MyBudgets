package de.mybudgets.app.ui.transactions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.google.android.material.snackbar.Snackbar
import de.mybudgets.app.R
import de.mybudgets.app.data.model.Transaction
import de.mybudgets.app.databinding.DialogRecurringPatternsBinding
import de.mybudgets.app.databinding.ItemRecurringPatternBinding
import de.mybudgets.app.util.CurrencyFormatter
import de.mybudgets.app.util.DateFormatter
import de.mybudgets.app.util.RecurringPatternDetector

class RecurringPatternDialog : DialogFragment() {

    private var _binding: DialogRecurringPatternsBinding? = null
    private val binding get() = _binding!!
    private var patterns: List<RecurringPatternDetector.RecurringPattern> = emptyList()
    private var onApply: ((List<Long>) -> Unit)? = null
    private var onDismiss: (() -> Unit)? = null
    private val checkedState = mutableMapOf<Int, Boolean>()

    fun setOnDismissListener(listener: () -> Unit): RecurringPatternDialog {
        onDismiss = listener
        return this
    }

    fun setPatterns(patterns: List<RecurringPatternDetector.RecurringPattern>): RecurringPatternDialog {
        this.patterns = patterns
        return this
    }

    fun setOnApplyListener(listener: (transactionIds: List<Long>) -> Unit): RecurringPatternDialog {
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
        dialog?.setTitle(getString(R.string.recurring_patterns_detected, patterns.size))

        patterns.indices.forEach { checkedState[it] = true }

        binding.tvEmpty.visibility = if (patterns.isEmpty()) View.VISIBLE else View.GONE
        binding.btnDiscard.setOnClickListener { dismiss() }
        binding.btnApply.setOnClickListener { applySelected() }
        buildPatternCards()
    }

    private fun buildPatternCards() {
        for ((idx, p) in patterns.withIndex()) {
            val card = ItemRecurringPatternBinding.inflate(layoutInflater, binding.containerPatterns, true)
            val tx = p.transactions.first()

            card.cbPattern.isChecked = checkedState[idx] ?: true
            card.cbPattern.setOnCheckedChangeListener { _, isChecked -> checkedState[idx] = isChecked }

            card.tvPatternDescription.text = p.suggestedDescription
            card.tvPatternAmount.text = CurrencyFormatter.format(tx.amount, "EUR")
            card.tvPatternInterval.text = p.intervalLabel
            card.tvPatternConfidence.text = "${(p.confidence * 100).toInt()}%"
            card.tvPatternDateRange.text = getString(R.string.recurring_pattern_date_range,
                DateFormatter.formatDate(p.transactions.first().date),
                DateFormatter.formatDate(p.transactions.last().date))
            card.tvTransactionCount.text = getString(R.string.recurring_pattern_tx_count, p.transactions.size)

            card.etPatternKeyword.setText(p.suggestedDescription)
            addSuggestionChips(card.chipGroupSuggestions, p.transactions, card.etPatternKeyword, card.layoutDetail, p.transactions)

            addTransactionRows(card.layoutDetail, p.transactions)
            card.tvPatternReason.text = p.reasoning

            card.ivExpand.setOnClickListener { toggleDetail(card.layoutDetail, card.ivExpand) }
            card.root.setOnClickListener { toggleDetail(card.layoutDetail, card.ivExpand) }
        }
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
        val selectedIds = mutableListOf<Long>()
        for ((idx, p) in patterns.withIndex()) {
            if (checkedState[idx] == true) {
                selectedIds.addAll(p.transactions.map { it.id })
            }
        }
        if (selectedIds.isNotEmpty()) {
            onApply?.invoke(selectedIds)
        }
        dismiss()
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
