package de.mybudgets.app.ui.transactions

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import de.mybudgets.app.R
import de.mybudgets.app.data.model.Transaction
import de.mybudgets.app.util.CurrencyFormatter
import de.mybudgets.app.util.RecurringPatternDetector

class RecurringPatternDialog : DialogFragment() {

    private var patterns: List<RecurringPatternDetector.RecurringPattern> = emptyList()
    private var onSavePattern: ((RecurringPatternDetector.RecurringPattern) -> Unit)? = null

    fun setPatterns(patterns: List<RecurringPatternDetector.RecurringPattern>): RecurringPatternDialog {
        this.patterns = patterns
        return this
    }

    fun setOnSavePatternListener(listener: (RecurringPatternDetector.RecurringPattern) -> Unit): RecurringPatternDialog {
        onSavePattern = listener
        return this
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val items = patterns.map { p ->
            val intervalStr = when {
                p.detectedIntervalDays % 365 == 0 -> "${p.detectedIntervalDays / 365} Jahr(e)"
                p.detectedIntervalDays % 30 == 0 -> "${p.detectedIntervalDays / 30} Monat(e)"
                else -> "${p.detectedIntervalDays} Tage"
            }
            val amount = p.transactions.firstOrNull()?.amount ?: 0.0
            val amountStr = CurrencyFormatter.format(amount, "EUR")
            val confidenceStr = "${(p.confidence * 100).toInt()}%"
            "${p.suggestedDescription}\n$amountStr · $intervalStr · Konfidenz: $confidenceStr"
        }.toTypedArray()

        return AlertDialog.Builder(requireActivity())
            .setTitle(getString(R.string.recurring_patterns_detected, patterns.size))
            .setItems(items) { _, which ->
                onSavePattern?.invoke(patterns[which])
            }
            .setPositiveButton(getString(android.R.string.ok), null)
            .create()
    }

    companion object {
        const val TAG = "RecurringPatternDialog"

        fun newInstance(
            patterns: List<RecurringPatternDetector.RecurringPattern>
        ): RecurringPatternDialog {
            return RecurringPatternDialog().setPatterns(patterns)
        }
    }
}
