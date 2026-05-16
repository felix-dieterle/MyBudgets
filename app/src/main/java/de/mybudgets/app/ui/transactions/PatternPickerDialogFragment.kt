package de.mybudgets.app.ui.transactions

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.mybudgets.app.R
import de.mybudgets.app.databinding.DialogPatternPickerBinding
import de.mybudgets.app.data.model.Transaction

/**
 * Dialog for selecting categorization pattern when user assigns category.
 * Offers:
 * 1. IBAN pattern (from tx.note if available)
 * 2. Text keyword pattern (extracted from tx.description)
 * 3. No pattern (one-time categorization only)
 */
class PatternPickerDialogFragment : DialogFragment() {

    private var _binding: DialogPatternPickerBinding? = null
    private val binding get() = _binding!!
    
    private var transaction: Transaction? = null
    private var onPatternSelected: ((patternType: String?, patternValue: String?) -> Unit)? = null
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogPatternPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val tx = transaction ?: run {
            dismiss()
            return
        }
        
        setupIbanOption(tx)
        setupTextOption(tx)
        setupButtons()
    }

    private fun setupIbanOption(tx: Transaction) {
        val iban = extractIban(tx.note)
        if (iban != null) {
            binding.radioIban.isEnabled = true
            binding.tvIbanValue.text = maskIban(iban)
            binding.tvIbanHint.text = "Alle Buchungen von diesem Empfänger"
        } else {
            binding.radioIban.isEnabled = false
            binding.tvIbanValue.text = "Keine IBAN verfügbar"
            binding.tvIbanHint.isVisible = false
        }
    }

    private fun setupTextOption(tx: Transaction) {
        // Extract keywords from description
        val keywords = extractKeywords(tx.description)
        
        if (keywords.isNotEmpty()) {
            binding.chipGroupKeywords.removeAllViews()
            keywords.forEach { keyword ->
                val chip = Chip(requireContext()).apply {
                    text = keyword
                    isCheckable = true
                    isChecked = false
                }
                binding.chipGroupKeywords.addView(chip)
            }
            binding.radioText.isEnabled = true
        } else {
            binding.radioText.isEnabled = false
            binding.tvTextHint.text = "Keine Schlüsselwörter gefunden"
        }
    }

    private fun setupButtons() {
        binding.btnCancel.setOnClickListener {
            dismiss()
        }
        
        binding.btnConfirm.setOnClickListener {
            when {
                binding.radioIban.isChecked -> {
                    val iban = extractIban(transaction?.note ?: "")
                    if (iban != null) {
                        onPatternSelected?.invoke("IBAN", iban)
                    }
                }
                binding.radioText.isChecked -> {
                    val selectedKeywords = getSelectedKeywords()
                    if (selectedKeywords.isNotEmpty()) {
                        val pattern = selectedKeywords.joinToString("|")
                        onPatternSelected?.invoke("TEXT", pattern)
                    }
                }
                binding.radioNone.isChecked -> {
                    onPatternSelected?.invoke(null, null)
                }
            }
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

    private fun extractKeywords(description: String): List<String> {
        val stopwords = setOf(
            "sagt", "danke", "ihr", "einkauf", "bei", "fuer", "fur",
            "von", "an", "mit", "der", "die", "das", "den", "dem"
        )
        
        return description
            .split(" ", "-", "/", ".")
            .map { it.trim().lowercase() }
            .filter { it.length >= 4 && it !in stopwords }
            .filter { it.any { c -> c.isLetter() } }
            .distinct()
            .take(5)
    }

    fun setTransaction(tx: Transaction): PatternPickerDialogFragment {
        this.transaction = tx
        return this
    }

    fun setOnPatternSelectedListener(listener: (patternType: String?, patternValue: String?) -> Unit): PatternPickerDialogFragment {
        this.onPatternSelected = listener
        return this
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(tx: Transaction): PatternPickerDialogFragment {
            return PatternPickerDialogFragment().setTransaction(tx)
        }
    }
}
