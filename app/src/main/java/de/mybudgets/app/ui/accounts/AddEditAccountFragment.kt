package de.mybudgets.app.ui.accounts

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
import de.mybudgets.app.viewmodel.AccountViewModel
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AddEditAccountFragment : Fragment() {

    private var _binding: FragmentAddEditAccountBinding? = null
    private val binding get() = _binding!!
    private val vm: AccountViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _binding = FragmentAddEditAccountBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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

        // Populate parent account spinner with real accounts
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.realAccounts.collect { realAccounts ->
                    val names = realAccounts.map { it.name }
                    binding.spinnerParentAccount.adapter =
                        ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, names)
                            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
                }
            }
        }

        binding.btnSave.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            if (name.isBlank()) { binding.etName.error = "Name fehlt"; return@setOnClickListener }

            val selectedType = AccountType.values()[binding.spinnerType.selectedItemPosition]
            val isVirtual = selectedType == AccountType.VIRTUAL

            var parentAccountId: Long? = null
            if (isVirtual) {
                val realAccounts = vm.realAccounts.value
                if (realAccounts.isEmpty()) {
                    Toast.makeText(requireContext(), getString(R.string.error_no_real_accounts), Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                parentAccountId = realAccounts[binding.spinnerParentAccount.selectedItemPosition].id
            }

            val account = Account(
                name            = name,
                type            = selectedType,
                iban            = binding.etIban.text.toString().trim(),
                bankCode        = binding.etBankCode.text.toString().trim(),
                isVirtual       = isVirtual,
                parentAccountId = parentAccountId
            )
            vm.save(account)
            findNavController().navigateUp()
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
