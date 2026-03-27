package de.mybudgets.app.ui.transfers

import android.os.Bundle
import android.view.*
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import de.mybudgets.app.R
import de.mybudgets.app.data.banking.FintsService
import de.mybudgets.app.data.model.TransferTemplate
import de.mybudgets.app.databinding.FragmentTransferBinding
import de.mybudgets.app.util.CurrencyFormatter
import de.mybudgets.app.viewmodel.TransferState
import de.mybudgets.app.viewmodel.TransferViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TransferFragment : Fragment() {

    private var _binding: FragmentTransferBinding? = null
    private val binding get() = _binding!!

    private val vm: TransferViewModel by viewModels()

    @Inject lateinit var fintsService: FintsService

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentTransferBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Register PIN and TAN providers that show dialogs
        fintsService.pinProvider = { bankName ->
            pinDialog(requireActivity(), getString(R.string.transfer_pin_title, bankName))
        }
        fintsService.tanProvider = { challenge ->
            tanDialog(requireActivity(), getString(R.string.transfer_tan_title, challenge))
        }

        // Populate source account spinner (all accounts – virtual and real)
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.accounts.collect { accounts ->
                    val labels = accounts.map {
                        val suffix = if (it.isVirtual) " (virtuell)" else ""
                        "${it.name}$suffix"
                    }
                    binding.spinnerFromAccount.adapter =
                        ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, labels)
                            .also { a -> a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
                }
            }
        }

        // Observe transfer state
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { state ->
                    when (state) {
                        is TransferState.Loading -> binding.btnExecuteTransfer.isEnabled = false
                        is TransferState.Success -> {
                            binding.btnExecuteTransfer.isEnabled = true
                            Snackbar.make(view, state.message, Snackbar.LENGTH_LONG).show()
                            vm.resetState()
                            findNavController().navigateUp()
                        }
                        is TransferState.Error -> {
                            binding.btnExecuteTransfer.isEnabled = true
                            Snackbar.make(view, state.message, Snackbar.LENGTH_LONG).show()
                            vm.resetState()
                        }
                        is TransferState.Idle -> binding.btnExecuteTransfer.isEnabled = true
                    }
                }
            }
        }

        binding.btnExecuteTransfer.setOnClickListener {
            val accounts = (binding.spinnerFromAccount.adapter as? ArrayAdapter<*>)?.let {
                vm.accounts.value
            } ?: emptyList()

            val fromAccount = accounts.getOrNull(binding.spinnerFromAccount.selectedItemPosition)
                ?: run {
                    Snackbar.make(view, getString(R.string.error_select_account), Snackbar.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

            val toName = binding.etRecipientName.text.toString().trim()
            val toIban = binding.etRecipientIban.text.toString().trim().replace(" ", "")
            val toBic  = binding.etRecipientBic.text.toString().trim()
            val amtStr = binding.etTransferAmount.text.toString().trim()
            val purpose = binding.etPurpose.text.toString().trim()

            if (toName.isBlank()) { binding.etRecipientName.error = getString(R.string.error_required); return@setOnClickListener }
            if (toIban.isBlank() || !isValidIban(toIban)) { binding.etRecipientIban.error = getString(R.string.error_invalid_iban); return@setOnClickListener }
            val amount = amtStr.toDoubleOrNull()
            if (amount == null || amount <= 0) { binding.etTransferAmount.error = getString(R.string.error_invalid_amount); return@setOnClickListener }
            if (fromAccount.bankCode.isBlank() || fromAccount.iban.isBlank()) {
                Snackbar.make(view, getString(R.string.error_account_missing_bank_data), Snackbar.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // Show confirmation dialog with full transfer details before sending to bank
            val confirmMsg = getString(
                R.string.transfer_confirm_message,
                toName,
                toIban,
                CurrencyFormatter.format(amount),
                purpose.ifBlank { "—" }
            )
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.transfer_confirm_title)
                .setMessage(confirmMsg)
                .setPositiveButton(R.string.transfer_confirm_ok) { _, _ ->
                    vm.executeTransfer(fromAccount, toName, toIban, toBic, amount, purpose)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        // Save as template
        binding.btnSaveTemplate.setOnClickListener {
            val toName  = binding.etRecipientName.text.toString().trim()
            val toIban  = binding.etRecipientIban.text.toString().trim().replace(" ", "")
            val amtStr  = binding.etTransferAmount.text.toString().trim()
            val amount  = amtStr.toDoubleOrNull()

            if (toName.isBlank() || toIban.isBlank() || amount == null || amount <= 0) {
                Snackbar.make(view, getString(R.string.template_error_fill_fields), Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val nameInput = EditText(requireContext()).apply {
                hint = getString(R.string.template_name_hint)
                setSingleLine()
            }
            val container = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                val dp16 = (16 * resources.displayMetrics.density).toInt()
                setPadding(dp16, 0, dp16, 0)
                addView(nameInput)
            }

            AlertDialog.Builder(requireContext())
                .setTitle(R.string.template_name_title)
                .setMessage(R.string.template_name_message)
                .setView(container)
                .setPositiveButton(R.string.save) { _, _ ->
                    val templateName = nameInput.text.toString().trim()
                    if (templateName.isBlank()) {
                        Snackbar.make(view, getString(R.string.template_error_name_required), Snackbar.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    val accounts = vm.accounts.value
                    val fromAccount = accounts.getOrNull(binding.spinnerFromAccount.selectedItemPosition)
                    val toBic   = binding.etRecipientBic.text.toString().trim()
                    val purpose = binding.etPurpose.text.toString().trim()

                    vm.saveTemplate(
                        name            = templateName,
                        sourceAccountId = fromAccount?.id ?: 0L,
                        toName          = toName,
                        toIban          = toIban,
                        toBic           = toBic,
                        amount          = amount,
                        purpose         = purpose
                    )
                    Snackbar.make(view, getString(R.string.template_saved), Snackbar.LENGTH_SHORT).show()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        // Load from template
        binding.btnLoadTemplate.setOnClickListener {
            val templates = vm.templates.value
            if (templates.isEmpty()) {
                Snackbar.make(view, getString(R.string.template_empty), Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showLoadTemplateDialog(templates, view)
        }
    }

    private fun showLoadTemplateDialog(templates: List<TransferTemplate>, view: View) {
        val names = templates.map { it.name }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.template_load)
            .setItems(names) { _, index ->
                applyTemplate(templates[index])
                Snackbar.make(view, getString(R.string.template_loaded), Snackbar.LENGTH_SHORT).show()
            }
            .setNeutralButton(R.string.template_manage_button) { _, _ ->
                showDeleteTemplateDialog(templates, view)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showDeleteTemplateDialog(templates: List<TransferTemplate>, view: View) {
        val names = templates.map { it.name }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.template_delete_title)
            .setItems(names) { _, index ->
                val template = templates[index]
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.template_delete_title)
                    .setMessage(getString(R.string.template_delete_message, template.name))
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        vm.deleteTemplate(template)
                        Snackbar.make(view, getString(R.string.template_deleted), Snackbar.LENGTH_SHORT).show()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun applyTemplate(template: TransferTemplate) {
        binding.etRecipientName.setText(template.recipientName)
        binding.etRecipientIban.setText(template.recipientIban)
        binding.etRecipientBic.setText(template.recipientBic)
        binding.etTransferAmount.setText(if (template.amount > 0) template.amount.toString() else "")
        binding.etPurpose.setText(template.purpose)

        // Select the matching source account in the spinner
        val accounts = vm.accounts.value
        val index = accounts.indexOfFirst { it.id == template.sourceAccountId }
        if (index >= 0) binding.spinnerFromAccount.setSelection(index)
    }

    private fun isValidIban(iban: String): Boolean {
        if (iban.length < 15 || iban.length > 34) return false
        if (!iban[0].isLetter() || !iban[1].isLetter()) return false
        if (!iban[2].isDigit() || !iban[3].isDigit()) return false
        // ISO 13616: move first 4 chars to end, replace letters A=10..Z=35, check mod97=1
        val rearranged = iban.substring(4) + iban.substring(0, 4)
        val numeric = rearranged.map { c ->
            if (c.isLetter()) (c.uppercaseChar() - 'A' + 10).toString() else c.toString()
        }.joinToString("")
        var remainder = 0
        for (ch in numeric) {
            remainder = (remainder * 10 + ch.digitToInt()) % 97
        }
        return remainder == 1
    }

    override fun onDestroyView() {
        fintsService.pinProvider = null
        fintsService.tanProvider = null
        super.onDestroyView()
        _binding = null
    }
}

