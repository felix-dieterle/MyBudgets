package de.mybudgets.app.ui.transactions

import android.os.Bundle
import android.view.*
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import de.mybudgets.app.data.model.Transaction
import de.mybudgets.app.data.model.TransactionType
import de.mybudgets.app.databinding.FragmentAddEditTransactionBinding
import de.mybudgets.app.viewmodel.AccountViewModel
import de.mybudgets.app.viewmodel.TransactionViewModel
import androidx.lifecycle.*
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AddEditTransactionFragment : Fragment() {

    private var _binding: FragmentAddEditTransactionBinding? = null
    private val binding get() = _binding!!
    private val vm: TransactionViewModel by viewModels()
    private val accountVm: AccountViewModel by viewModels()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentAddEditTransactionBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val types = TransactionType.values().map { it.name }
        binding.spinnerType.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, types)

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                accountVm.accounts.collect { accounts ->
                    val names = accounts.map {
                        if (it.isVirtual) "${it.name} (virtuell)" else it.name
                    }
                    binding.spinnerAccount.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, names)
                }
            }
        }

        binding.btnSave.setOnClickListener {
            val desc = binding.etDescription.text.toString().trim()
            val amtStr = binding.etAmount.text.toString().trim()
            if (desc.isBlank()) { binding.etDescription.error = "Beschreibung fehlt"; return@setOnClickListener }
            val amount = amtStr.toDoubleOrNull() ?: 0.0
            val accounts = accountVm.accounts.value
            val selectedAccount = accounts.getOrNull(binding.spinnerAccount.selectedItemPosition) ?: return@setOnClickListener
            val accountId = if (selectedAccount.isVirtual) {
                selectedAccount.parentAccountId ?: selectedAccount.id
            } else selectedAccount.id
            val tx = Transaction(
                accountId   = accountId,
                virtualAccountId = if (selectedAccount.isVirtual) selectedAccount.id else null,
                description = desc,
                amount      = amount,
                type        = TransactionType.values()[binding.spinnerType.selectedItemPosition],
                note        = binding.etNote.text.toString().trim()
            )
            vm.save(tx)
            findNavController().navigateUp()
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
