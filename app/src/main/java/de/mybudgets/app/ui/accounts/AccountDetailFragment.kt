package de.mybudgets.app.ui.accounts

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.work.*
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import de.mybudgets.app.R
import de.mybudgets.app.data.banking.FintsService
import de.mybudgets.app.data.model.Account
import de.mybudgets.app.data.model.AccountType
import de.mybudgets.app.databinding.FragmentAccountDetailBinding
import de.mybudgets.app.ui.transfers.pinDialog
import de.mybudgets.app.ui.transfers.tanDialog
import de.mybudgets.app.util.CurrencyFormatter
import de.mybudgets.app.viewmodel.AccountViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AccountDetailFragment : Fragment() {

    private var _binding: FragmentAccountDetailBinding? = null
    private val binding get() = _binding!!
    private val vm: AccountViewModel by viewModels()
    private var accountId: Long = 0L

    @Inject lateinit var fintsService: FintsService

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _binding = FragmentAccountDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        accountId = arguments?.getLong("accountId") ?: 0L

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.accounts.collect { list ->
                    list.find { it.id == accountId }?.let { showAccount(it, list) }
                }
            }
        }

        binding.btnBankSync.setOnClickListener {
            val account = vm.accounts.value.find { it.id == accountId } ?: return@setOnClickListener
            if (account.bankCode.isBlank() || account.iban.isBlank()) {
                Snackbar.make(view, getString(R.string.error_account_missing_bank_data), Snackbar.LENGTH_LONG).show()
                return@setOnClickListener
            }

            fintsService.pinProvider = { bankName ->
                pinDialog(requireActivity(), getString(R.string.transfer_pin_title, bankName))
            }
            fintsService.tanProvider = { challenge ->
                tanDialog(requireActivity(), getString(R.string.transfer_tan_title, challenge))
            }

            Snackbar.make(view, getString(R.string.bank_sync_started), Snackbar.LENGTH_SHORT).show()

            val request = OneTimeWorkRequestBuilder<de.mybudgets.app.worker.BankSyncWorker>()
                .setInputData(workDataOf("account_id" to accountId))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(requireContext()).enqueue(request)
        }
    }

    private fun showAccount(acc: Account, allAccounts: List<Account>) {
        binding.tvAccountName.text    = acc.name
        binding.tvAccountBalance.text = CurrencyFormatter.format(acc.balance, acc.currency)
        binding.tvAccountIban.text    = if (acc.iban.isNotBlank()) "IBAN: ${acc.iban}" else ""
        val typeLabel = when (acc.type) {
            AccountType.CHECKING -> "Girokonto"
            AccountType.SAVINGS  -> "Sparkonto"
            AccountType.CASH     -> "Barkasse"
            AccountType.VIRTUAL  -> "Virtuelles Konto"
        }
        binding.tvAccountType.text = typeLabel

        // Show sync button only for accounts with bank data
        binding.btnBankSync.visibility = if (acc.bankCode.isNotBlank() || acc.iban.isNotBlank()) View.VISIBLE else View.GONE

        if (acc.isVirtual && acc.parentAccountId != null) {
            val parent = allAccounts.find { it.id == acc.parentAccountId }
            if (parent != null) {
                binding.layoutLinkedAccount.visibility = View.VISIBLE
                binding.tvLinkedAccountName.text = parent.name
            }
        } else {
            binding.layoutLinkedAccount.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        fintsService.pinProvider = null
        fintsService.tanProvider = null
        super.onDestroyView()
        _binding = null
    }
}

