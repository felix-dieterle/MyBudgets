package de.mybudgets.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.mybudgets.app.data.banking.FintsService
import de.mybudgets.app.data.model.Account
import de.mybudgets.app.data.model.RecurringRule
import de.mybudgets.app.data.repository.AccountRepository
import de.mybudgets.app.data.repository.RecurringRuleRepository
import de.mybudgets.app.data.repository.TransactionRepository
import de.mybudgets.app.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "AccountViewModel"

sealed class BankSyncState {
    object Idle : BankSyncState()
    data class Loading(
        val phase: SyncPhase = SyncPhase.SESSION_SETUP,
        val detailMessage: String = ""
    ) : BankSyncState() {
        override fun toString() = "$phase${if (detailMessage.isNotBlank()) ": $detailMessage" else ""}"
    }
    data class Success(val importedCount: Int, val balance: Double? = null) : BankSyncState()
    data class Error(val message: String, val phase: SyncPhase? = null) : BankSyncState()
}

enum class SyncPhase(val displayName: String) {
    SESSION_SETUP("Session-Setup"),
    BIC_LOOKUP("BIC-Abfrage"),
    JOB_SELECTION("Job-Auswahl"),
    EXECUTE("Bank-Anfrage"),
    PARSE_RESULT("Daten-Verarbeitung"),
    IMPORT("Import lokal");
}

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val repo: AccountRepository,
    private val txRepo: TransactionRepository,
    private val ruleRepo: RecurringRuleRepository,
    private val fintsService: FintsService
) : ViewModel() {

    val accounts = repo.observeAll().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val realAccounts = repo.observeRealAccounts().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val totalBalance = repo.observeTotalBalance().stateIn(viewModelScope, SharingStarted.Lazily, 0.0)

    private val accountIdFilter = MutableStateFlow(0L)

    fun selectAccount(accountId: Long) { accountIdFilter.value = accountId }

    val accountTransactions = accountIdFilter
        .flatMapLatest { id -> if (id == 0L) flowOf(emptyList()) else txRepo.observeByAccount(id) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _bankSyncState = MutableStateFlow<BankSyncState>(BankSyncState.Idle)
    val bankSyncState: StateFlow<BankSyncState> = _bankSyncState

    // Anchor for "weiter zurück laden": hält fest, welches fromDate zuletzt
    // an die Bank geschickt wurde. Bei Voll-Sync (fromDate=null) wird der
    // earliest neue TX als initialer Anchor verwendet.
    // Wird NIE auf null gesetzt – nur auf NO_FROM_DATE (= kein weiteres Laden möglich).
    private var syncLastFromDate: Long = NO_FROM_DATE

    val canContinueSync: Boolean get() = syncLastFromDate != NO_FROM_DATE && syncLastFromDate > SYNC_STOP_MILLIS

    fun continueSyncOlder(accountId: Long) {
        AppLogger.i(TAG, "═══════════════════════════════════════════════════════════")
        AppLogger.i(TAG, "continueSyncOlder: START für Account=$accountId")
        AppLogger.i(TAG, "  canContinueSync=$canContinueSync")
        AppLogger.i(TAG, "  syncLastFromDate=$syncLastFromDate (${if (syncLastFromDate == NO_FROM_DATE) "NO_FROM_DATE" else java.util.Date(syncLastFromDate)})")
        
        if (!canContinueSync) {
            AppLogger.w(TAG, "continueSyncOlder: ABBRUCH - canContinueSync=false")
            return
        }
        
        viewModelScope.launch {
            // Historischer Sync: Springe 365 Tage zurück (nicht 1 Tag) für schnelleres Laden
            val earliest = txRepo.getEarliestDateForAccount(accountId)
            AppLogger.i(TAG, "  DB: earliest TX date=$earliest (${if (earliest == null) "LEER" else java.util.Date(earliest)})")
            AppLogger.i(TAG, "  SYNC_STOP_MILLIS=${java.util.Date(SYNC_STOP_MILLIS)} (2000-01-01)")
            
            if (earliest == null || earliest <= SYNC_STOP_MILLIS) { 
                syncLastFromDate = NO_FROM_DATE
                AppLogger.w(TAG, "continueSyncOlder: ABBRUCH - earliest null oder zu alt, syncLastFromDate → NO_FROM_DATE")
                return@launch 
            }
            
            val fromDate = earliest - 365L * 24 * 60 * 60 * 1000 // -365 Tage
            AppLogger.i(TAG, "  Berechnet: fromDate=$fromDate (${java.util.Date(fromDate)}) = earliest - 365 Tage")
            
            if (fromDate <= SYNC_STOP_MILLIS) { 
                syncLastFromDate = NO_FROM_DATE
                AppLogger.w(TAG, "continueSyncOlder: ABBRUCH - fromDate <= SYNC_STOP_MILLIS, syncLastFromDate → NO_FROM_DATE")
                return@launch 
            }
            
            AppLogger.i(TAG, "continueSyncOlder: ✅ Starte Sync mit fromDate=${java.util.Date(fromDate)}")
            syncBankTransactions(accountId, fromDate)
        }
    }

    private suspend fun matchTransactionsAgainstRules(transactions: List<de.mybudgets.app.data.model.Transaction>, accountId: Long) {
        val rules = ruleRepo.getActive()
        val matchingRules = if (accountId != 0L) rules.filter { it.accountId == null || it.accountId == accountId } else rules
        for (tx in transactions) {
            val matchingRule = matchingRules
                .mapNotNull { rule ->
                    val descNote = "${tx.description} ${tx.note}"
                    if (!descNote.contains(rule.matchKeyword, ignoreCase = true)) return@mapNotNull null
                    val ibanOk = rule.matchIban?.let { descNote.contains(it, ignoreCase = true) } ?: true
                    if (!ibanOk) return@mapNotNull null
                    val tolerance = rule.matchAmountTolerance ?: 1.0
                    val amountOk = rule.matchAmount == null ||
                        kotlin.math.abs(kotlin.math.abs(tx.amount) - rule.matchAmount) <= tolerance
                    if (!amountOk) return@mapNotNull null
                    val priority = when {
                        rule.matchIban != null -> 3
                        rule.matchAmount != null -> 2
                        else -> 1
                    }
                    Pair(rule, priority)
                }
                .maxByOrNull { it.second }
                ?.first
            if (matchingRule != null) {
                txRepo.save(tx.copy(
                    isRecurring = true,
                    recurringIntervalDays = matchingRule.intervalDays,
                    categoryId = matchingRule.categoryId ?: tx.categoryId
                ))
            }
        }
    }

    fun saveRecurringRule(rule: RecurringRule) = viewModelScope.launch {
        try {
            ruleRepo.save(rule)
        } catch (e: Exception) {
            AppLogger.e(TAG, "saveRecurringRule fehlgeschlagen: ${e.message}", e)
        }
    }

    fun save(account: Account) = viewModelScope.launch { repo.save(account) }
    fun delete(account: Account) = viewModelScope.launch { repo.delete(account) }

    fun syncBankTransactions(accountId: Long, fromDateMillis: Long = NO_FROM_DATE) = viewModelScope.launch {
        AppLogger.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        AppLogger.i(TAG, "syncBankTransactions: START")
        AppLogger.i(TAG, "  accountId=$accountId")
        AppLogger.i(TAG, "  fromDateMillis=$fromDateMillis (${if (fromDateMillis == NO_FROM_DATE) "NO_FROM_DATE = Voll-Sync" else java.util.Date(fromDateMillis)})")
        
        _bankSyncState.value = BankSyncState.Loading(phase = SyncPhase.SESSION_SETUP)
        // AtomicReference so the syncPhaseUpdateHandler (called from the IO thread) and the
        // onFailure handler (running on Main) can both access lastPhase without a data race.
        val lastPhaseRef = java.util.concurrent.atomic.AtomicReference(SyncPhase.SESSION_SETUP)

        try {
            val account = repo.getById(accountId)
            if (account == null) {
                _bankSyncState.value = BankSyncState.Error("Konto nicht gefunden", SyncPhase.SESSION_SETUP)
                return@launch
            }
            if (account.iban.isBlank()) {
                _bankSyncState.value = BankSyncState.Error("IBAN fehlt", SyncPhase.SESSION_SETUP)
                return@launch
            }
            if (account.userId.isBlank()) {
                _bankSyncState.value = BankSyncState.Error("Nutzerkennung fehlt", SyncPhase.SESSION_SETUP)
                return@launch
            }
            if (fintsService.pinProvider == null) {
                _bankSyncState.value = BankSyncState.Error("PIN-Dialog nicht verfügbar", SyncPhase.SESSION_SETUP)
                return@launch
            }

            // Called from the IO thread inside fetchAccountStatement; MutableStateFlow.value
            // is thread-safe and AtomicReference ensures lastPhaseRef is safely shared.
            fintsService.syncPhaseUpdateHandler = { phaseTag, detail ->
                val phase = when (phaseTag) {
                    "1-setup" -> SyncPhase.SESSION_SETUP
                    "2-bic"   -> SyncPhase.BIC_LOOKUP
                    "3-job"   -> SyncPhase.JOB_SELECTION
                    "4-exec"  -> SyncPhase.EXECUTE
                    "5-parse" -> SyncPhase.PARSE_RESULT
                    else      -> null
                }
                if (phase != null) {
                    lastPhaseRef.set(phase)
                    _bankSyncState.value = BankSyncState.Loading(phase = phase, detailMessage = detail)
                }
            }

            val fromDate: java.util.Date?
            val actualFromMillis: Long
            if (fromDateMillis != NO_FROM_DATE) {
                fromDate = java.util.Date(fromDateMillis)
                actualFromMillis = fromDateMillis
                AppLogger.i(TAG, "  Sync-Typ: HISTORISCH mit fromDate=${fromDate}")
            } else {
                AppLogger.i(TAG, "  Sync-Typ: VOLL-SYNC (kein Datumsfilter)")
                fromDate = null
                actualFromMillis = NO_FROM_DATE
            }
            
            AppLogger.i(TAG, "  → Rufe FintsService.fetchAccountStatement auf...")
            val syncResult = fintsService.fetchAccountStatement(account, fromDate)

            syncResult.onSuccess { transactions ->
                AppLogger.i(TAG, "  ✅ Bank-Response erfolgreich:")
                AppLogger.i(TAG, "    transactions.size=${transactions.size}")
                if (transactions.isNotEmpty()) {
                    val oldest = transactions.minOfOrNull { it.date }
                    val newest = transactions.maxOfOrNull { it.date }
                    AppLogger.i(TAG, "    Zeitraum: ${if (oldest != null) java.util.Date(oldest) else "?"} bis ${if (newest != null) java.util.Date(newest) else "?"}")
                }
                
                _bankSyncState.value = BankSyncState.Loading(phase = SyncPhase.IMPORT, detailMessage = "${transactions.size} Buchungen werden importiert...")
                val existingRemoteIds = txRepo.getAllRemoteIds()
                AppLogger.i(TAG, "    existingRemoteIds.size=${existingRemoteIds.size} (bereits in DB)")
                
                val newTx = transactions.filter { it.remoteId == null || it.remoteId !in existingRemoteIds }
                AppLogger.i(TAG, "    newTx.size=${newTx.size} (nach Deduplizierung)")
                
                newTx.forEach { tx -> txRepo.save(tx.copy(accountId = account.id)) }
                matchTransactionsAgainstRules(newTx, account.id)
                
                // Anchor für "weiter zurück": bei explizitem fromDate das fromDate selbst,
                // bei Voll-Sync den earliest GELIEFERTEN TX verwenden (nicht earliest neue!).
                val oldAnchor = syncLastFromDate
                syncLastFromDate = if (actualFromMillis != NO_FROM_DATE) {
                    actualFromMillis
                } else {
                    transactions.minOfOrNull { it.date } ?: NO_FROM_DATE
                }
                AppLogger.i(TAG, "    Anchor-Update:")
                AppLogger.i(TAG, "      ALT: syncLastFromDate=$oldAnchor (${if (oldAnchor == NO_FROM_DATE) "NO_FROM_DATE" else java.util.Date(oldAnchor)})")
                AppLogger.i(TAG, "      NEU: syncLastFromDate=$syncLastFromDate (${if (syncLastFromDate == NO_FROM_DATE) "NO_FROM_DATE" else java.util.Date(syncLastFromDate)})")
                
                val camtBalance = fintsService.lastCamtBalance
                AppLogger.i(TAG, "    camtBalance=$camtBalance")
                
                // Balance nur bei Voll-Sync aktualisieren (historischer Sync liefert alten Saldo!)
                if (camtBalance != null && actualFromMillis == NO_FROM_DATE) {
                    repo.save(account.copy(balance = camtBalance))
                    AppLogger.i(TAG, "    ✅ Saldo ${account.id} aktualisiert: $camtBalance (Voll-Sync)")
                } else if (camtBalance != null) {
                    AppLogger.i(TAG, "    ⚠️ Saldo NICHT aktualisiert - Historischer Sync (CLBD=$camtBalance ist veraltet)")
                }
                
                val updatedAccount = repo.getById(accountId)
                _bankSyncState.value = BankSyncState.Success(newTx.size, updatedAccount?.balance)
                AppLogger.i(TAG, "  🎉 SYNC ERFOLG: ${newTx.size} neue Buchungen, Saldo=${updatedAccount?.balance}")
                AppLogger.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            }.onFailure { e ->
                AppLogger.e(TAG, "  ❌ Bank-Response FEHLER: ${e.message}", e)
                AppLogger.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                _bankSyncState.value = BankSyncState.Error(e.message ?: "Synchronisation fehlgeschlagen", lastPhaseRef.get())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(TAG, "syncBankTransactions: Unerwarteter Fehler: ${e.message}", e)
            _bankSyncState.value = BankSyncState.Error(e.message ?: "Synchronisation fehlgeschlagen", lastPhaseRef.get())
        } finally {
            fintsService.syncPhaseUpdateHandler = null
        }
    }

    fun resetBankSyncState() {
        _bankSyncState.value = BankSyncState.Idle
    }

    companion object {
        const val NO_FROM_DATE = -1L
        private val SYNC_STOP_MILLIS = java.util.Calendar.getInstance().apply {
            set(2000, 0, 1, 0, 0, 0)
        }.timeInMillis
    }
}
