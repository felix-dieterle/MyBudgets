package de.mybudgets.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.mybudgets.app.data.model.Transaction
import de.mybudgets.app.data.repository.TransactionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TransactionViewModel @Inject constructor(
    private val repo: TransactionRepository
) : ViewModel() {

    val transactions = repo.observeAll().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun save(tx: Transaction) = viewModelScope.launch { repo.save(tx) }
    fun delete(tx: Transaction) = viewModelScope.launch { repo.delete(tx) }
}
