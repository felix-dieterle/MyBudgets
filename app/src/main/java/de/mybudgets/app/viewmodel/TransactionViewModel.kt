package de.mybudgets.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.mybudgets.app.data.model.Transaction
import de.mybudgets.app.data.model.TransactionType
import de.mybudgets.app.data.model.TransactionWithCategory
import de.mybudgets.app.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TransactionViewModel @Inject constructor(
    private val repo: TransactionRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    fun setSearchQuery(query: String) { _searchQuery.value = query }

    val transactions = repo.observeAll().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val transactionsWithCategory = repo.observeAllWithCategory().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val searchedTransactions = combine(transactionsWithCategory, _searchQuery) { list, query ->
        if (query.isBlank()) list
        else {
            val q = query.trim().lowercase()
            list.filter { (tx, cat) ->
                tx.description.lowercase().contains(q) ||
                tx.note.lowercase().contains(q) ||
                cat?.name?.lowercase()?.contains(q) == true
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    suspend fun suggestCategoryId(description: String, amount: Double, type: TransactionType): Long? =
        repo.suggestCategoryId(description, amount, type)

    fun save(tx: Transaction) = viewModelScope.launch { repo.save(tx) }
    fun delete(tx: Transaction) = viewModelScope.launch { repo.delete(tx) }
}
