package de.mybudgets.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.mybudgets.app.data.repository.AccountRepository
import de.mybudgets.app.data.repository.TransactionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val accountRepo: AccountRepository,
    private val txRepo: TransactionRepository
) : ViewModel() {

    val totalBalance = accountRepo.observeTotalBalance().stateIn(viewModelScope, SharingStarted.Lazily, 0.0)
    val accounts     = accountRepo.observeAll().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val transactions = txRepo.observeAll().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}
