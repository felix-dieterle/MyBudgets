package de.mybudgets.app.ui.transactions

import android.os.Bundle
import android.view.*
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import de.mybudgets.app.R
import de.mybudgets.app.data.model.Category
import de.mybudgets.app.data.model.Transaction
import de.mybudgets.app.data.model.TransactionType
import de.mybudgets.app.databinding.FragmentAddEditTransactionBinding
import de.mybudgets.app.viewmodel.AccountViewModel
import de.mybudgets.app.viewmodel.CategoryViewModel
import de.mybudgets.app.viewmodel.TransactionViewModel
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AddEditTransactionFragment : Fragment() {

    private var _binding: FragmentAddEditTransactionBinding? = null
    private val binding get() = _binding!!
    private val vm: TransactionViewModel by viewModels()
    private val accountVm: AccountViewModel by viewModels()
    private val categoryVm: CategoryViewModel by viewModels()
    private var categories: List<Category> = emptyList()

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

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                categoryVm.categories.collect { loaded ->
                    categories = loaded
                    val names = mutableListOf(getString(R.string.tx_category_none)).apply {
                        addAll(loaded.map { it.name })
                    }
                    binding.spinnerCategory.adapter =
                        ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, names)
                }
            }
        }

        binding.spinnerType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                binding.tvCategorySuggestion.text = ""
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
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
            val selectedType = TransactionType.values()[binding.spinnerType.selectedItemPosition]
            val manuallySelectedCategoryId = categories.getOrNull(binding.spinnerCategory.selectedItemPosition - 1)?.id

            viewLifecycleOwner.lifecycleScope.launch {
                val suggestedCategoryId = if (manuallySelectedCategoryId == null) {
                    vm.suggestCategoryId(desc, amount, selectedType)
                } else null

                if (manuallySelectedCategoryId == null && suggestedCategoryId != null) {
                    val suggestedName = categories.firstOrNull { it.id == suggestedCategoryId }?.name
                        ?: "#$suggestedCategoryId"
                    binding.tvCategorySuggestion.text =
                        getString(R.string.tx_category_suggested, suggestedName)

                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.tx_category_suggestion_title)
                        .setMessage(getString(R.string.tx_category_suggestion_message, suggestedName))
                        .setPositiveButton(R.string.tx_category_apply) { _, _ ->
                            saveTransaction(
                                accountId = accountId,
                                virtualAccountId = if (selectedAccount.isVirtual) selectedAccount.id else null,
                                description = desc,
                                amount = amount,
                                type = selectedType,
                                note = binding.etNote.text.toString().trim(),
                                categoryId = suggestedCategoryId
                            )
                        }
                        .setNegativeButton(R.string.tx_category_skip) { _, _ ->
                            saveTransaction(
                                accountId = accountId,
                                virtualAccountId = if (selectedAccount.isVirtual) selectedAccount.id else null,
                                description = desc,
                                amount = amount,
                                type = selectedType,
                                note = binding.etNote.text.toString().trim(),
                                categoryId = null
                            )
                        }
                        .setNeutralButton(R.string.tx_category_manual, null)
                        .show()
                } else {
                    saveTransaction(
                        accountId = accountId,
                        virtualAccountId = if (selectedAccount.isVirtual) selectedAccount.id else null,
                        description = desc,
                        amount = amount,
                        type = selectedType,
                        note = binding.etNote.text.toString().trim(),
                        categoryId = manuallySelectedCategoryId
                    )
                }
            }
        }
    }

    private fun saveTransaction(
        accountId: Long,
        virtualAccountId: Long?,
        description: String,
        amount: Double,
        type: TransactionType,
        note: String,
        categoryId: Long?
    ) {
        val tx = Transaction(
            accountId = accountId,
            virtualAccountId = virtualAccountId,
            description = description,
            amount = amount,
            type = type,
            categoryId = categoryId,
            note = note
        )
        vm.save(tx)
        findNavController().navigateUp()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
