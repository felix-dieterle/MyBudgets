package de.mybudgets.app.ui.accounts

import android.os.Bundle
import android.view.*
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import de.mybudgets.app.data.model.Account
import de.mybudgets.app.data.model.AccountType
import de.mybudgets.app.databinding.FragmentAddEditAccountBinding
import de.mybudgets.app.viewmodel.AccountViewModel

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

        val types = AccountType.values().map { it.name }
        binding.spinnerType.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, types)

        binding.btnSave.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            if (name.isBlank()) { binding.etName.error = "Name fehlt"; return@setOnClickListener }
            val account = Account(
                name     = name,
                type     = AccountType.values()[binding.spinnerType.selectedItemPosition],
                iban     = binding.etIban.text.toString().trim(),
                bankCode = binding.etBankCode.text.toString().trim(),
                isVirtual = binding.spinnerType.selectedItemPosition == AccountType.VIRTUAL.ordinal
            )
            vm.save(account)
            findNavController().navigateUp()
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
