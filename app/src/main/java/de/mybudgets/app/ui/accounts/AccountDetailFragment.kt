package de.mybudgets.app.ui.accounts

import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import de.mybudgets.app.R
import de.mybudgets.app.data.banking.FintsService
import de.mybudgets.app.data.model.Account
import de.mybudgets.app.data.model.AccountType
import de.mybudgets.app.databinding.FragmentAccountDetailBinding
import de.mybudgets.app.ui.transactions.TransactionAdapter
import de.mybudgets.app.ui.transactions.RecurringPatternDialog
import de.mybudgets.app.ui.transfers.pinDialog
import de.mybudgets.app.ui.transfers.tanDialog
import de.mybudgets.app.ui.transfers.decoupledConfirmDialog
import de.mybudgets.app.util.AppLogger
import de.mybudgets.app.util.CurrencyFormatter
import de.mybudgets.app.util.RecurringPatternDetector
import de.mybudgets.app.viewmodel.AccountViewModel
import de.mybudgets.app.viewmodel.BankSyncState
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
    private var pendingRecurrenceCheck = false

    @Inject lateinit var fintsService: FintsService

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _binding = FragmentAccountDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        accountId = arguments?.getLong("accountId") ?: 0L

        txAdapter = TransactionAdapter { item ->
            val bundle = Bundle().apply { putLong("transactionId", item.transaction.id) }
            findNavController().navigate(R.id.action_accountDetailFragment_to_transactionDetailFragment, bundle)
        }
        binding.rvAccountTransactions.adapter = txAdapter
        vm.selectAccount(accountId)

        val prefs = requireContext().getSharedPreferences("mybudgets_prefs", Context.MODE_PRIVATE)
        prefs.getString("migration_info", null)?.let { info ->
            prefs.edit().remove("migration_info").apply()
            Snackbar.make(view, getString(R.string.db_migration_done, info), Snackbar.LENGTH_LONG).show()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    vm.accounts.collect { list ->
                        list.find { it.id == accountId }?.let { showAccount(it, list) }
                    }
                }
                launch {
                    vm.accountTransactions.collect { txList ->
                        txAdapter.submitList(txList.map { tx -> 
                            de.mybudgets.app.data.model.TransactionWithCategory(tx, null) 
                        })
                        binding.tvNoAccountTransactions.visibility =
                            if (txList.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    vm.hasHistoricalGaps.collect { hasGaps ->
                        binding.btnLoadMoreHistory.visibility = if (hasGaps) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    vm.nextGapDate.collect { date ->
                        if (date != null) {
                            binding.btnLoadMoreHistory.text = getString(R.string.bank_load_more_history, date)
                        }
                    }
                }
                launch {
                    vm.bankSyncState.collect { state ->
                        when (state) {
                            is BankSyncState.Idle -> {
                                binding.progressSync.visibility = View.GONE
                                binding.tvSyncStatus.visibility = View.GONE
                            }
                            is BankSyncState.Loading -> {
                                binding.progressSync.visibility = View.VISIBLE
                                binding.tvSyncStatus.visibility = View.VISIBLE
                                val phaseLabel = state.phase.displayName
                                binding.tvSyncStatus.text = if (state.detailMessage.isNotBlank())
                                    "$phaseLabel: ${state.detailMessage}" else phaseLabel
                            }
                            is BankSyncState.Success -> {
                                binding.progressSync.visibility = View.GONE
                                binding.tvSyncStatus.visibility = View.GONE
                                vm.resetBankSyncState()
                                showSyncResultSnackbar(state)
                                // Update Button-Status nach Sync (mit kleinem Delay für DB-Commit)
                                viewLifecycleOwner.lifecycleScope.launch {
                                    kotlinx.coroutines.delay(100) // DB-Insert abwarten
                                    updateHistoricalSyncButtonState()
                                    
                                    // Recurrence check nur wenn keine weiteren Gaps
                                    if (state.importedCount > 0) {
                                        val hasMoreGaps = vm.canContinueSync(accountId)
                                        if (hasMoreGaps) {
                                            // Noch Lücken → defer check
                                            pendingRecurrenceCheck = true
                                        } else {
                                            // Keine Lücken mehr → jetzt checken
                                            checkForRecurringPatterns {}
                                        }
                                    }
                                }
                            }
                            is BankSyncState.Error -> {
                                binding.progressSync.visibility = View.GONE
                                binding.tvSyncStatus.visibility = View.GONE
                                Snackbar.make(requireView(), state.message, Snackbar.LENGTH_LONG).show()
                                vm.resetBankSyncState()
                            }
                        }
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
            vm.syncBankTransactions(accountId, AccountViewModel.NO_FROM_DATE)
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
            registerPinTanProviders()
            Snackbar.make(view, getString(R.string.bank_sync_started), Snackbar.LENGTH_SHORT).show()
            vm.continueSyncOlder(accountId)
        }

        binding.btnLoadMoreHistory.setOnClickListener {
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
            Snackbar.make(view, getString(R.string.bank_historical_sync_started), Snackbar.LENGTH_SHORT).show()
            vm.continueSyncOlder(accountId)
        }
        
        // Initial Button-Status setzen
        updateHistoricalSyncButtonState()
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────
    
    private fun updateHistoricalSyncButtonState() {
        viewLifecycleOwner.lifecycleScope.launch {
            val canContinue = vm.canContinueSync(accountId)
            binding.btnHistoricalSync.isEnabled = canContinue
            binding.btnHistoricalSync.alpha = if (canContinue) 1.0f else 0.5f
            vm.updateGapState(accountId)
        }
    }

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
        
        // Update button state based on sync intervals
        if (hasBankData) {
            updateHistoricalSyncButtonState()
        }

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

    private fun checkForRecurringPatterns(onDismiss: () -> Unit) {
        try {
            AppLogger.i("AccountDetailFragment", "checkForRecurringPatterns: START")
            val transactions = vm.accountTransactions.value
            AppLogger.i("AccountDetailFragment", "  transactions.size=${transactions.size}")
            
            if (transactions.isEmpty()) { 
                AppLogger.i("AccountDetailFragment", "  → Keine Transaktionen, abbruch")
                onDismiss()
                return 
            }
            
            AppLogger.i("AccountDetailFragment", "  → Rufe RecurringPatternDetector auf...")
            val patterns = RecurringPatternDetector.detectPatterns(transactions)
            AppLogger.i("AccountDetailFragment", "  → patterns.size=${patterns.size}")
            
            if (patterns.isNotEmpty()) {
                val appCtx = requireActivity().applicationContext
                AppLogger.i("AccountDetailFragment", "  → Zeige RecurringPatternDialog")
                RecurringPatternDialog.newInstance(patterns)
                    .setOnApplyListener { rules ->
                        try {
                            AppLogger.i("AccountDetailFragment", "applyRecurringRules: Speichere ${rules.size} Rules")
                            rules.forEach { rule -> 
                                try {
                                    vm.saveRecurringRule(rule)
                                    AppLogger.i("AccountDetailFragment", "  ✅ Rule gespeichert: ${rule.name}")
                                } catch (e: Exception) {
                                    AppLogger.e("AccountDetailFragment", "  ❌ Rule speichern fehlgeschlagen: ${rule.name}", e)
                                }
                            }
                            android.widget.Toast.makeText(appCtx,
                                appCtx.getString(R.string.recurring_patterns_apply, rules.size),
                                android.widget.Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            AppLogger.e("AccountDetailFragment", "applyRecurringRules failed", e)
                            android.widget.Toast.makeText(appCtx, "Fehler beim Speichern: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                    .setOnDismissListener { 
                        AppLogger.i("AccountDetailFragment", "RecurringPatternDialog dismissed")
                        try {
                            onDismiss()
                        } catch (e: Exception) {
                            AppLogger.e("AccountDetailFragment", "onDismiss callback failed", e)
                        }
                    }
                    .show(childFragmentManager, RecurringPatternDialog.TAG)
            } else {
                AppLogger.i("AccountDetailFragment", "  → Keine Patterns gefunden")
                onDismiss()
            }
        } catch (e: Exception) {
            AppLogger.e("AccountDetailFragment", "checkForRecurringPatterns CRASH", e)
            try {
                android.widget.Toast.makeText(requireContext(), "Pattern-Erkennung fehlgeschlagen: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            } catch (_: Exception) {}
            try {
                onDismiss()
            } catch (_: Exception) {}
        }
    }

    private fun showSyncResultSnackbar(state: BankSyncState.Success) {
        val balanceStr = if (state.balance != null) {
            " · ${CurrencyFormatter.format(state.balance)}"
        } else ""
        val dateRangeStr = if (state.dateRangeMessage != null) {
            " (${ state.dateRangeMessage})"
        } else ""
        val msg = getString(R.string.bank_sync_result, state.importedCount) + balanceStr + dateRangeStr
        Snackbar.make(requireView(), msg, Snackbar.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        // Trigger pending recurrence check if user leaves screen with gaps
        if (pendingRecurrenceCheck) {
            checkForRecurringPatterns {}
        }
        
        fintsService.pinProvider = null
        fintsService.tanProvider = null
        fintsService.decoupledConfirmProvider = null
        super.onDestroyView()
        _binding = null
    }
}
