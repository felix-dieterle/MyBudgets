package de.mybudgets.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.mybudgets.app.data.banking.FintsService
import de.mybudgets.app.data.model.Account
import de.mybudgets.app.data.repository.AccountRepository
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
    data class Success(val importedCount: Int) : BankSyncState()
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

    fun save(account: Account) = viewModelScope.launch { repo.save(account) }
    fun delete(account: Account) = viewModelScope.launch { repo.delete(account) }

    fun syncBankTransactions(accountId: Long, fromDateMillis: Long = NO_FROM_DATE) = viewModelScope.launch {
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
                } ?: return@syncPhaseUpdateHandler
                lastPhaseRef.set(phase)
                _bankSyncState.value = BankSyncState.Loading(phase = phase, detailMessage = detail)
            }

            val fromDate = if (fromDateMillis != NO_FROM_DATE) java.util.Date(fromDateMillis) else null
            val syncResult = fintsService.fetchAccountStatement(account, fromDate)

            syncResult.onSuccess { transactions ->
                _bankSyncState.value = BankSyncState.Loading(phase = SyncPhase.IMPORT, detailMessage = "${transactions.size} Buchungen werden importiert...")
                val existingRemoteIds = txRepo.getAllRemoteIds()
                val newTx = transactions.filter { it.remoteId == null || it.remoteId !in existingRemoteIds }
                newTx.forEach { tx -> txRepo.save(tx.copy(accountId = account.id)) }
                _bankSyncState.value = BankSyncState.Success(newTx.size)
                AppLogger.i(TAG, "syncBankTransactions: ${newTx.size} neue Buchungen für Konto ${account.id}")
            }.onFailure { e ->
                AppLogger.e(TAG, "syncBankTransactions fehlgeschlagen: ${e.message}", e)
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
    }
}
