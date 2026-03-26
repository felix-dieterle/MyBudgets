package de.mybudgets.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.mybudgets.app.data.banking.FintsService
import de.mybudgets.app.data.model.Account
import de.mybudgets.app.data.model.StandingOrder
import de.mybudgets.app.data.repository.AccountRepository
import de.mybudgets.app.data.repository.StandingOrderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class StandingOrderState {
    object Idle : StandingOrderState()
    object Loading : StandingOrderState()
    data class Success(val message: String) : StandingOrderState()
    data class Error(val message: String) : StandingOrderState()
}

@HiltViewModel
class StandingOrderViewModel @Inject constructor(
    private val repo: StandingOrderRepository,
    private val accountRepo: AccountRepository,
    private val fintsService: FintsService
) : ViewModel() {

    val orders = repo.observeAll().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val accounts = accountRepo.observeAll().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _state = MutableStateFlow<StandingOrderState>(StandingOrderState.Idle)
    val state: StateFlow<StandingOrderState> = _state

    fun save(order: StandingOrder, sendToBank: Boolean = false) = viewModelScope.launch {
        _state.value = StandingOrderState.Loading
        try {
            repo.save(order)

            if (sendToBank && fintsService.pinProvider != null) {
                val account = accountRepo.getById(order.sourceAccountId) ?: run {
                    _state.value = StandingOrderState.Error("Konto nicht gefunden")
                    return@launch
                }
                val realAccount = if (account.isVirtual && account.parentAccountId != null) {
                    accountRepo.getById(account.parentAccountId) ?: account
                } else account

                val result = fintsService.createStandingOrder(realAccount, order)
                result.onSuccess { msg ->
                    repo.save(order.copy(sentToBank = true))
                    _state.value = StandingOrderState.Success(msg)
                }.onFailure { e ->
                    _state.value = StandingOrderState.Error("Lokal gespeichert, Bank-Fehler: ${e.message}")
                }
            } else {
                _state.value = StandingOrderState.Success("Dauerauftrag lokal gespeichert")
            }
        } catch (e: Exception) {
            _state.value = StandingOrderState.Error(e.message ?: "Fehler beim Speichern")
        }
    }

    fun delete(order: StandingOrder) = viewModelScope.launch { repo.delete(order) }

    fun resetState() { _state.value = StandingOrderState.Idle }
}
