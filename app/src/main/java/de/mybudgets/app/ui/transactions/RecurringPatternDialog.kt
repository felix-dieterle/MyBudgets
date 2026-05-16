package de.mybudgets.app.ui.transactions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.DialogFragment
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import de.mybudgets.app.R
import de.mybudgets.app.databinding.DialogRecurringPatternsBinding
import de.mybudgets.app.databinding.ItemRecurringPatternBinding
import de.mybudgets.app.util.CurrencyFormatter
import de.mybudgets.app.util.DateFormatter
import de.mybudgets.app.util.RecurringPatternDetector

class RecurringPatternDialog : DialogFragment() {

    private var _binding: DialogRecurringPatternsBinding? = null
    private val binding get() = _binding!!
    private var patterns: List<RecurringPatternDetector.RecurringPattern> = emptyList()

    fun setPatterns(patterns: List<RecurringPatternDetector.RecurringPattern>): RecurringPatternDialog {
        this.patterns = patterns
        return this
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogRecurringPatternsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.setTitle(getString(R.string.recurring_patterns_detected, patterns.size))
        binding.btnClose.setOnClickListener { dismiss() }
        buildPatternCards()
    }

    private fun buildPatternCards() {
        for (p in patterns) {
            val card = ItemRecurringPatternBinding.inflate(layoutInflater, binding.containerPatterns, true)
            val tx = p.transactions.first()
            card.tvPatternDescription.text = p.suggestedDescription
            card.tvPatternAmount.text = CurrencyFormatter.format(tx.amount, "EUR")
            card.tvPatternInterval.text = p.intervalLabel
            card.tvPatternConfidence.text = "${(p.confidence * 100).toInt()}%"
            card.tvPatternReason.text = p.reasoning
            card.tvTransactionCount.text = getString(R.string.recurring_pattern_tx_count, p.transactions.size)
            val range = "${DateFormatter.formatDate(p.transactions.first().date)} – ${DateFormatter.formatDate(p.transactions.last().date)}"
            card.tvPatternDateRange.text = range

            card.root.setOnClickListener {
                Snackbar.make(requireView(), p.suggestedDescription, Snackbar.LENGTH_SHORT).show()
            }
        }
        binding.tvEmpty.visibility = if (patterns.isEmpty()) View.VISIBLE else View.GONE
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
