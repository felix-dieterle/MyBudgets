package de.mybudgets.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.mybudgets.app.data.model.Account
import de.mybudgets.app.data.repository.AccountRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val repo: AccountRepository
) : ViewModel() {

    val accounts = repo.observeAll().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val realAccounts = repo.observeRealAccounts().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val totalBalance = repo.observeTotalBalance().stateIn(viewModelScope, SharingStarted.Lazily, 0.0)

    fun save(account: Account) = viewModelScope.launch { repo.save(account) }
    fun delete(account: Account) = viewModelScope.launch { repo.delete(account) }
}
