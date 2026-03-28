package de.mybudgets.app.ui.accounts

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.navigation.fragment.findNavController
import androidx.work.*
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import de.mybudgets.app.R
import de.mybudgets.app.data.banking.FintsService
import de.mybudgets.app.data.model.Account
import de.mybudgets.app.data.model.AccountType
import de.mybudgets.app.databinding.FragmentAccountDetailBinding
import de.mybudgets.app.ui.transactions.TransactionAdapter
import de.mybudgets.app.ui.transfers.pinDialog
import de.mybudgets.app.ui.transfers.tanDialog
import de.mybudgets.app.ui.transfers.decoupledConfirmDialog
import de.mybudgets.app.util.CurrencyFormatter
import de.mybudgets.app.viewmodel.AccountViewModel
import de.mybudgets.app.worker.BankSyncWorker
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@AndroidEntryPoint
class AccountDetailFragment : Fragment() {

    private var _binding: FragmentAccountDetailBinding? = null
    private val binding get() = _binding!!
    private val vm: AccountViewModel by viewModels()
    private var accountId: Long = 0L
    private lateinit var txAdapter: TransactionAdapter

    @Inject lateinit var fintsService: FintsService

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _binding = FragmentAccountDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        accountId = arguments?.getLong("accountId") ?: 0L

        txAdapter = TransactionAdapter { tx ->
            val bundle = Bundle().apply { putLong("transactionId", tx.id) }
            findNavController().navigate(R.id.action_accountDetailFragment_to_transactionDetailFragment, bundle)
        }
        binding.rvAccountTransactions.adapter = txAdapter
        vm.selectAccount(accountId)

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    vm.accounts.collect { list ->
                        list.find { it.id == accountId }?.let { showAccount(it, list) }
                    }
                }
                launch {
                    vm.accountTransactions.collect { txList ->
                        txAdapter.submitList(txList)
                        binding.tvNoAccountTransactions.visibility =
                            if (txList.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
            }
        }

        binding.btnEditAccount.setOnClickListener {
            val bundle = Bundle().apply { putLong("accountId", accountId) }
            findNavController().navigate(R.id.action_accountDetailFragment_to_addEditAccountFragment, bundle)
        }

        binding.btnBankSync.setOnClickListener {
            val account = vm.accounts.value.find { it.id == accountId } ?: return@setOnClickListener
            if (account.iban.isBlank()) {
                Snackbar.make(view, getString(R.string.error_account_missing_iban), Snackbar.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (account.userId.isBlank()) {
                Snackbar.make(view, getString(R.string.error_account_missing_user_id), Snackbar.LENGTH_LONG).show()
                return@setOnClickListener
            }
            registerPinTanProviders()
            Snackbar.make(view, getString(R.string.bank_sync_started), Snackbar.LENGTH_SHORT).show()
            enqueueBankSync(fromDateMillis = BankSyncWorker.NO_FROM_DATE, tag = "bank_sync_$accountId")
        }

        binding.btnHistoricalSync.setOnClickListener {
            val account = vm.accounts.value.find { it.id == accountId } ?: return@setOnClickListener
            if (account.iban.isBlank()) {
                Snackbar.make(view, getString(R.string.error_account_missing_iban), Snackbar.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (account.userId.isBlank()) {
                Snackbar.make(view, getString(R.string.error_account_missing_user_id), Snackbar.LENGTH_LONG).show()
                return@setOnClickListener
            }
            showDatePickerForHistoricalSync()
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private fun registerPinTanProviders() {
        fintsService.pinProvider = { bankName ->
            pinDialog(requireActivity(), getString(R.string.transfer_pin_title, bankName))
        }
        fintsService.tanProvider = { challenge ->
            tanDialog(requireActivity(), getString(R.string.transfer_tan_title, challenge))
        }
        fintsService.decoupledConfirmProvider = { challenge ->
            decoupledConfirmDialog(requireActivity(), challenge)
        }
    }

    private fun showDatePickerForHistoricalSync() {
        val cal = Calendar.getInstance().apply {
            // Default start date: 1 year ago
            add(Calendar.YEAR, -1)
        }
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                val fromCal = Calendar.getInstance().apply {
                    set(year, month, day, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                registerPinTanProviders()
                Snackbar.make(requireView(), getString(R.string.bank_historical_sync_started), Snackbar.LENGTH_SHORT).show()
                enqueueBankSync(fromDateMillis = fromCal.timeInMillis, tag = "bank_historical_sync_$accountId")
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.maxDate = System.currentTimeMillis()
            setTitle(getString(R.string.bank_historical_sync_dialog_title))
        }.show()
    }

    private fun enqueueBankSync(fromDateMillis: Long, tag: String) {
        val inputData = workDataOf(
            BankSyncWorker.KEY_ACCOUNT_ID to accountId,
            BankSyncWorker.KEY_FROM_DATE  to fromDateMillis
        )
        val request = OneTimeWorkRequestBuilder<BankSyncWorker>()
            .setInputData(inputData)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag(tag)
            .build()
        val workManager = WorkManager.getInstance(requireContext())
        workManager.enqueue(request)

        // Observe the work to show result feedback
        workManager.getWorkInfoByIdLiveData(request.id).observe(viewLifecycleOwner) { info ->
            if (info?.state == WorkInfo.State.SUCCEEDED) {
                val count = info.outputData.getInt(BankSyncWorker.KEY_IMPORTED_COUNT, 0)
                Snackbar.make(
                    requireView(),
                    getString(R.string.bank_sync_result, count),
                    Snackbar.LENGTH_LONG
                ).show()
            } else if (info?.state == WorkInfo.State.FAILED) {
                val errorMessage = info.outputData.getString(BankSyncWorker.KEY_ERROR_MESSAGE)
                val message = if (!errorMessage.isNullOrBlank()) errorMessage
                              else getString(R.string.bank_sync_failed)
                Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG).show()
            }
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

        // Show sync buttons only for accounts with bank data
        val hasBankData = acc.bankCode.isNotBlank() || acc.iban.isNotBlank()
        binding.btnBankSync.visibility       = if (hasBankData) View.VISIBLE else View.GONE
        binding.btnHistoricalSync.visibility = if (hasBankData) View.VISIBLE else View.GONE

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
        fintsService.decoupledConfirmProvider = null
        super.onDestroyView()
        _binding = null
    }
}
