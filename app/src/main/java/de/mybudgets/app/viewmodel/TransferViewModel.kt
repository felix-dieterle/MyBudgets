package de.mybudgets.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.mybudgets.app.data.banking.FintsService
import de.mybudgets.app.data.model.Account
import de.mybudgets.app.data.model.Transaction
import de.mybudgets.app.data.model.TransactionType
import de.mybudgets.app.data.repository.AccountRepository
import de.mybudgets.app.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    private val fintsService: FintsService
) : ViewModel() {

    val accounts = accountRepo.observeAll().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val realAccounts = accountRepo.observeRealAccounts().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

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
        _state.value = TransferState.Loading

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
            // Record local transaction
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
            _state.value = TransferState.Success(msg)
        }.onFailure { e ->
            _state.value = TransferState.Error(e.message ?: "Unbekannter Fehler")
        }
    }

    fun resetState() { _state.value = TransferState.Idle }
}
