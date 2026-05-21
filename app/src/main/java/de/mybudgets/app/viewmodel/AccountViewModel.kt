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
    data class Success(
        val importedCount: Int,
        val balance: Double? = null,
        val dateRangeMessage: String? = null
    ) : BankSyncState()
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
    private val syncIntervalRepo: de.mybudgets.app.data.repository.SyncIntervalRepository,
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

    suspend fun canContinueSync(accountId: Long): Boolean {
        val nextDate = syncIntervalRepo.getNextHistoricalSyncDate(accountId)
        // null = Lücke geschlossen, Button deaktivieren
        // 0L oder >0L = Weiterer Sync möglich, Button aktivieren
        return nextDate != null
    }

    fun continueSyncOlder(accountId: Long) {
        AppLogger.i(TAG, "═══════════════════════════════════════════════════════════")
        AppLogger.i(TAG, "continueSyncOlder: START für Account=$accountId")
        
        viewModelScope.launch {
            val nextDate = syncIntervalRepo.getNextHistoricalSyncDate(accountId)
            AppLogger.i(TAG, "  getNextHistoricalSyncDate() = $nextDate")
            
            if (nextDate == null) {
                AppLogger.w(TAG, "continueSyncOlder: ABBRUCH - Alles vollständig geladen")
                return@launch
            }
            
            AppLogger.i(TAG, "continueSyncOlder: ✅ Starte historischen Sync")
            syncBankTransactions(accountId, nextDate, isHistorical = true)
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

    fun syncBankTransactions(accountId: Long, fromDateMillis: Long = NO_FROM_DATE, isHistorical: Boolean = false) = viewModelScope.launch {
        AppLogger.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        AppLogger.i(TAG, "syncBankTransactions: START")
        AppLogger.i(TAG, "  accountId=$accountId")
        AppLogger.i(TAG, "  fromDateMillis=$fromDateMillis (${if (fromDateMillis == NO_FROM_DATE) "NO_FROM_DATE = Auto" else java.util.Date(fromDateMillis)})")
        AppLogger.i(TAG, "  isHistorical=$isHistorical")
        
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
            val syncTypeMessage: String
            
            // Auto-Mode: fromDateMillis == NO_FROM_DATE
            // Wenn DB leer → Voll-Sync (fromDate=null)
            // Wenn DB hat TX → Normal-Sync ab neuester TX - 7 Tage (um Lücken zu schließen)
            if (fromDateMillis == NO_FROM_DATE && !isHistorical) {
                val newestTxDate = txRepo.getLatestDateForAccount(accountId)
                if (newestTxDate != null) {
                    // Normal-Sync: Lade ab neuester TX - 7 Tage
                    val fromMillis = newestTxDate - 7L * 24 * 60 * 60 * 1000
                    fromDate = java.util.Date(fromMillis)
                    val sdf = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.GERMANY)
                    syncTypeMessage = "Lade ab ${sdf.format(fromDate)}..."
                    AppLogger.i(TAG, "  Sync-Typ: NORMAL (DB hat TX, lade ab neueste - 7 Tage)")
                } else {
                    // Voll-Sync: DB leer, lade alles
                    fromDate = null
                    syncTypeMessage = "Erstmaliger Sync - lade alle Buchungen..."
                    AppLogger.i(TAG, "  Sync-Typ: VOLL-SYNC (DB leer)")
                }
            } else if (isHistorical && fromDateMillis == 0L) {
                // Historischer Start-Sync: fromDateMillis == 0L bedeutet "ohne fromDate"
                fromDate = null
                syncTypeMessage = "Lade älteste Buchungen..."
                AppLogger.i(TAG, "  Sync-Typ: HISTORISCH-START (ohne fromDate, Bank entscheidet)")
            } else if (isHistorical) {
                // Explizites fromDate (continueSyncOlder oder Gap-Closing)
                fromDate = java.util.Date(fromDateMillis)
                val sdf = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.GERMANY)
                syncTypeMessage = "Lade ab ${sdf.format(fromDate)}..."
                AppLogger.i(TAG, "  Sync-Typ: HISTORISCH-CONTINUE mit fromDate=${fromDate}")
            } else {
                // Fallback (sollte nicht vorkommen)
                fromDate = java.util.Date(fromDateMillis)
                val sdf = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.GERMANY)
                syncTypeMessage = "Lade ab ${sdf.format(fromDate)}..."
                AppLogger.i(TAG, "  Sync-Typ: FALLBACK mit fromDate=${fromDate}")
            }
            
            _bankSyncState.value = BankSyncState.Loading(phase = SyncPhase.EXECUTE, detailMessage = syncTypeMessage)
            AppLogger.i(TAG, "  → Rufe FintsService.fetchAccountStatement auf...")
            val syncResult = fintsService.fetchAccountStatement(account, fromDate)

            syncResult.onSuccess { transactions ->
                AppLogger.i(TAG, "  ✅ Bank-Response erfolgreich:")
                AppLogger.i(TAG, "    transactions.size=${transactions.size}")
                
                val dateRangeMsg = if (transactions.isNotEmpty()) {
                    val oldest = transactions.minOfOrNull { it.date }
                    val newest = transactions.maxOfOrNull { it.date }
                    val sdf = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.GERMANY)
                    AppLogger.i(TAG, "    Zeitraum: ${if (oldest != null) java.util.Date(oldest) else "?"} bis ${if (newest != null) java.util.Date(newest) else "?"}")
                    "${sdf.format(oldest)} - ${sdf.format(newest)}"
                } else {
                    null
                }
                
                _bankSyncState.value = BankSyncState.Loading(phase = SyncPhase.IMPORT, detailMessage = "${transactions.size} Buchungen werden importiert...")
                val existingRemoteIds = txRepo.getAllRemoteIds()
                AppLogger.i(TAG, "    existingRemoteIds.size=${existingRemoteIds.size} (bereits in DB)")
                
                val newTx = transactions.filter { it.remoteId == null || it.remoteId !in existingRemoteIds }
                AppLogger.i(TAG, "    newTx.size=${newTx.size} (nach Deduplizierung)")
                
                newTx.forEach { tx -> txRepo.save(tx.copy(accountId = account.id)) }
                matchTransactionsAgainstRules(newTx, account.id)
                
                // Speichere Sync-Intervall für Gap-Detection
                if (transactions.isNotEmpty()) {
                    val startDate = transactions.minOfOrNull { it.date } ?: 0L
                    val endDate = transactions.maxOfOrNull { it.date } ?: 0L
                    val interval = de.mybudgets.app.data.model.SyncInterval(
                        accountId = account.id,
                        startDate = startDate,
                        endDate = endDate,
                        isHistorical = isHistorical
                    )
                    syncIntervalRepo.insert(interval)
                    AppLogger.i(TAG, "    ✅ Sync-Intervall gespeichert: ${java.util.Date(startDate)} bis ${java.util.Date(endDate)}, isHistorical=$isHistorical")
                }
                
                val camtBalance = fintsService.lastCamtBalance
                AppLogger.i(TAG, "    camtBalance=$camtBalance")
                
                // Balance nur aktualisieren wenn die gelieferten Daten AKTUELL sind
                // (nicht wenn Bank alte Daten liefert, auch bei Voll-Sync!)
                val shouldUpdateBalance = if (camtBalance != null && transactions.isNotEmpty()) {
                    val newestTxDate = transactions.maxOfOrNull { it.date } ?: 0L
                    val daysSinceNewestTx = (System.currentTimeMillis() - newestTxDate) / (24 * 60 * 60 * 1000)
                    AppLogger.i(TAG, "    Balance-Check:")
                    AppLogger.i(TAG, "      newestTxDate=${java.util.Date(newestTxDate)}")
                    AppLogger.i(TAG, "      daysSinceNewestTx=$daysSinceNewestTx Tage")
                    daysSinceNewestTx <= 7 // Balance nur aktualisieren wenn neueste TX max 7 Tage alt
                } else {
                    false
                }
                
                if (shouldUpdateBalance && camtBalance != null) {
                    val oldBalance = account.balance
                    repo.save(account.copy(balance = camtBalance))
                    AppLogger.i(TAG, "    ✅ Saldo ${account.id} aktualisiert: $oldBalance → $camtBalance")
                } else if (camtBalance != null) {
                    AppLogger.i(TAG, "    ⚠️ Saldo NICHT aktualisiert - Daten zu alt oder historischer Sync (CLBD=$camtBalance)")
                }
                
                val updatedAccount = repo.getById(accountId)
                _bankSyncState.value = BankSyncState.Success(newTx.size, updatedAccount?.balance, dateRangeMsg)
                AppLogger.i(TAG, "  🎉 SYNC ERFOLG: ${newTx.size} neue Buchungen, Saldo=${updatedAccount?.balance}, Zeitraum=$dateRangeMsg")
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
