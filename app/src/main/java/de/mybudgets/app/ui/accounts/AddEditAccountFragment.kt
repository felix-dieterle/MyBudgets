package de.mybudgets.app.ui.accounts

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.*
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import de.mybudgets.app.R
import de.mybudgets.app.data.model.Account
import de.mybudgets.app.data.model.AccountType
import de.mybudgets.app.databinding.FragmentAddEditAccountBinding
import de.mybudgets.app.util.DateFormatter
import de.mybudgets.app.viewmodel.AccountViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Calendar

@AndroidEntryPoint
class AddEditAccountFragment : Fragment() {

    private var _binding: FragmentAddEditAccountBinding? = null
    private val binding get() = _binding!!
    private val vm: AccountViewModel by viewModels()

    private var editAccountId: Long = 0L
    /** Holds the original account when editing, so we can preserve immutable fields like balance/createdAt. */
    private var originalAccount: Account? = null
    private var selectedTargetDueDate: Long? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _binding = FragmentAddEditAccountBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        editAccountId = arguments?.getLong("accountId", 0L) ?: 0L

        val typeLabels = AccountType.values().map { type ->
            when (type) {
                AccountType.CHECKING -> "Girokonto"
                AccountType.SAVINGS  -> "Sparkonto"
                AccountType.CASH     -> "Barkasse"
                AccountType.VIRTUAL  -> "Virtuelles Konto"
            }
        }
        binding.spinnerType.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, typeLabels)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        // Show/hide parent account section depending on selected type
        binding.spinnerType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val isVirtual = AccountType.values()[pos] == AccountType.VIRTUAL
                binding.layoutParentAccount.visibility = if (isVirtual) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        // Populate parent account spinner with real accounts, and prefill form when editing.
        // Combine both flows so prefill always uses fresh realAccounts alongside loaded accounts.
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.realAccounts
                    .combine(vm.accounts) { realAccounts, allAccounts -> Pair(realAccounts, allAccounts) }
                    .collect { (realAccounts, allAccounts) ->
                        val names = realAccounts.map { it.name }
                        binding.spinnerParentAccount.adapter =
                            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, names)
                                .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

                        // Pre-fill once when in edit mode
                        if (editAccountId != 0L && originalAccount == null) {
                            allAccounts.find { it.id == editAccountId }?.let { acc ->
                                originalAccount = acc
                                prefillForm(acc, realAccounts)
                            }
                        }
                    }
            }
        }

        binding.etVirtualTargetDueDate.setOnClickListener { openDueDatePicker() }
        binding.etVirtualTargetDueDate.setOnLongClickListener {
            selectedTargetDueDate = null
            binding.etVirtualTargetDueDate.setText("")
            true
        }

        binding.btnSave.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            if (name.isBlank()) { binding.etName.error = getString(R.string.error_required); return@setOnClickListener }

            val selectedType = AccountType.values()[binding.spinnerType.selectedItemPosition]
            val isVirtual = selectedType == AccountType.VIRTUAL

            var parentAccountId: Long? = null
            val pattern = if (isVirtual) binding.etVirtualPattern.text.toString().trim() else ""
            val targetAmount = if (isVirtual) binding.etVirtualTargetAmount.text.toString().trim().toDoubleOrNull() else null
            val targetDueDate = if (isVirtual) selectedTargetDueDate else null
            if (pattern.isNotBlank() && runCatching { Regex(pattern) }.isFailure) {
                binding.etVirtualPattern.error = getString(R.string.error_invalid_regex)
                return@setOnClickListener
            }
            if (isVirtual) {
                val realAccounts = vm.realAccounts.value
                if (realAccounts.isEmpty()) {
                    Toast.makeText(requireContext(), getString(R.string.error_no_real_accounts), Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                parentAccountId = realAccounts[binding.spinnerParentAccount.selectedItemPosition].id
            }

            val existing = originalAccount
            val account = if (existing != null) {
                // Edit mode: preserve immutable fields (id, balance, createdAt, icon, color)
                existing.copy(
                    name            = name,
                    type            = selectedType,
                    iban            = binding.etIban.text.toString().trim(),
                    bankCode        = binding.etBankCode.text.toString().trim(),
                    userId          = binding.etUserId.text.toString().trim(),
                    tanMethod       = binding.etTanMethod.text.toString().trim(),
                    isVirtual       = isVirtual,
                    parentAccountId = parentAccountId,
                    autoAssignPattern = pattern,
                    targetAmount    = targetAmount,
                    targetDueDate   = targetDueDate,
                    updatedAt       = System.currentTimeMillis()
                )
            } else {
                // Create mode
                Account(
                    name            = name,
                    type            = selectedType,
                    iban            = binding.etIban.text.toString().trim(),
                    bankCode        = binding.etBankCode.text.toString().trim(),
                    userId          = binding.etUserId.text.toString().trim(),
                    tanMethod       = binding.etTanMethod.text.toString().trim(),
                    isVirtual       = isVirtual,
                    parentAccountId = parentAccountId,
                    autoAssignPattern = pattern,
                    targetAmount    = targetAmount,
                    targetDueDate   = targetDueDate
                )
            }
            vm.save(account)
            findNavController().navigateUp()
        }
    }

    private fun prefillForm(acc: Account, realAccounts: List<Account>) {
        binding.etName.setText(acc.name)
        binding.etIban.setText(acc.iban)
        binding.etBankCode.setText(acc.bankCode)
        binding.etUserId.setText(acc.userId)
        binding.etTanMethod.setText(acc.tanMethod)

        val typePos = AccountType.values().indexOf(acc.type)
        if (typePos >= 0) binding.spinnerType.setSelection(typePos)

        if (acc.isVirtual && acc.parentAccountId != null) {
            val parentPos = realAccounts.indexOfFirst { it.id == acc.parentAccountId }
            if (parentPos >= 0) binding.spinnerParentAccount.setSelection(parentPos)
        }
        binding.etVirtualPattern.setText(acc.autoAssignPattern)
        binding.etVirtualTargetAmount.setText(acc.targetAmount?.toString().orEmpty())
        selectedTargetDueDate = acc.targetDueDate
        binding.etVirtualTargetDueDate.setText(acc.targetDueDate?.let(DateFormatter::formatDate).orEmpty())

        // Update toolbar label to "Konto bearbeiten"
        activity?.title = getString(R.string.edit_account)
    }

    private fun openDueDatePicker() {
        val cal = Calendar.getInstance().apply {
            timeInMillis = selectedTargetDueDate ?: System.currentTimeMillis()
        }
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                val selected = Calendar.getInstance().apply {
                    set(year, month, day, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                selectedTargetDueDate = selected.timeInMillis
                binding.etVirtualTargetDueDate.setText(DateFormatter.formatDate(selected.timeInMillis))
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
