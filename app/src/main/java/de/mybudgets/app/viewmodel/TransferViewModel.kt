package de.mybudgets.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.mybudgets.app.data.banking.FintsService
import de.mybudgets.app.data.model.Account
import de.mybudgets.app.data.model.Transaction
import de.mybudgets.app.data.model.TransactionType
import de.mybudgets.app.data.model.TransferTemplate
import de.mybudgets.app.data.repository.AccountRepository
import de.mybudgets.app.data.repository.TransactionRepository
import de.mybudgets.app.data.repository.TransferTemplateRepository
import de.mybudgets.app.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "TransferViewModel"

sealed class TransferState {
    object Idle : TransferState()
    object Loading : TransferState()
    data class Success(val message: String) : TransferState()
    data class Error(val message: String) : TransferState()
}

@HiltViewModel
class TransferViewModel @Inject constructor(
    private val accountRepo: AccountRepository,
    private val transactionRepo: TransactionRepository,
    private val fintsService: FintsService,
    private val templateRepo: TransferTemplateRepository
) : ViewModel() {

    val accounts = accountRepo.observeAll().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val realAccounts = accountRepo.observeRealAccounts().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val templates = templateRepo.observeAll().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _state = MutableStateFlow<TransferState>(TransferState.Idle)
    val state: StateFlow<TransferState> = _state

    fun executeTransfer(
        fromAccount: Account,
        toName: String,
        toIban: String,
        toBic: String,
        amount: Double,
        purpose: String
    ) = viewModelScope.launch {
        AppLogger.i(TAG, "executeTransfer gestartet – von ${fromAccount.name} an $toName ($toIban) Betrag=$amount")
        _state.value = TransferState.Loading

        try {
            // Resolve virtual → real account for FinTS
            val realAccount = if (fromAccount.isVirtual && fromAccount.parentAccountId != null) {
                accountRepo.getById(fromAccount.parentAccountId) ?: fromAccount
            } else {
                fromAccount
            }

            val result = fintsService.executeTransfer(
                fromAccount = realAccount,
                toName      = toName,
                toIban      = toIban,
                toBic       = toBic,
                amount      = amount,
                purpose     = purpose
            )

            result.onSuccess { msg ->
                // Record local transaction; failure here must not crash the app
                try {
                    transactionRepo.save(
                        Transaction(
                            accountId        = realAccount.id,
                            virtualAccountId = if (fromAccount.isVirtual) fromAccount.id else null,
                            amount           = amount,
                            description      = "Überweisung an $toName",
                            type             = TransactionType.EXPENSE,
                            note             = purpose
                        )
                    )
                    AppLogger.i(TAG, "executeTransfer: Lokale Buchung gespeichert")
                } catch (e: CancellationException) {
                    throw e // Always propagate coroutine cancellation
                } catch (e: Exception) {
                    AppLogger.w(TAG, "executeTransfer: Lokale Buchung fehlgeschlagen (nicht kritisch): ${e.message}", e)
                }
                _state.value = TransferState.Success(msg)
            }.onFailure { e ->
                AppLogger.e(TAG, "executeTransfer fehlgeschlagen: ${e.message}", e)
                _state.value = TransferState.Error(e.message ?: "Unbekannter Fehler")
            }
        } catch (e: CancellationException) {
            throw e // Always propagate coroutine cancellation
        } catch (e: Exception) {
            AppLogger.e(TAG, "executeTransfer: Unerwarteter Fehler: ${e.message}", e)
            _state.value = TransferState.Error(e.message ?: "Unbekannter Fehler")
        }
    }

    fun resetState() { _state.value = TransferState.Idle }

    fun saveTemplate(
        name: String,
        sourceAccountId: Long,
        toName: String,
        toIban: String,
        toBic: String,
        amount: Double,
        purpose: String
    ) = viewModelScope.launch {
        AppLogger.i(TAG, "Vorlage speichern: $name")
        try {
            templateRepo.save(
                TransferTemplate(
                    name = name,
                    sourceAccountId = sourceAccountId,
                    recipientName = toName,
                    recipientIban = toIban,
                    recipientBic = toBic,
                    amount = amount,
                    purpose = purpose
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(TAG, "Vorlage speichern fehlgeschlagen: ${e.message}", e)
        }
    }

    fun deleteTemplate(template: TransferTemplate) = viewModelScope.launch {
        AppLogger.i(TAG, "Vorlage löschen: ${template.name}")
        try {
            templateRepo.delete(template)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(TAG, "Vorlage löschen fehlgeschlagen: ${e.message}", e)
        }
    }
}
