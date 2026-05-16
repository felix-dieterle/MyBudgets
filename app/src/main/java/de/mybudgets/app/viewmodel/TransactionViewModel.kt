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

    private val _dateFrom = MutableStateFlow(0L)
    private val _dateTo = MutableStateFlow(0L)
    private val _amountMin = MutableStateFlow<Double?>(null)
    private val _amountMax = MutableStateFlow<Double?>(null)

    val dateFrom: StateFlow<Long> = _dateFrom
    val dateTo: StateFlow<Long> = _dateTo
    val amountMin: StateFlow<Double?> = _amountMin
    val amountMax: StateFlow<Double?> = _amountMax

    fun setDateFrom(millis: Long) { _dateFrom.value = millis }
    fun setDateTo(millis: Long) { _dateTo.value = millis }
    fun setAmountMin(v: Double?) { _amountMin.value = v }
    fun setAmountMax(v: Double?) { _amountMax.value = v }

    fun clearFilters() {
        _searchQuery.value = ""
        _dateFrom.value = 0L
        _dateTo.value = 0L
        _amountMin.value = null
        _amountMax.value = null
    }

    val hasActiveFilters: Boolean get() =
        _searchQuery.value.isNotBlank() ||
        _dateFrom.value > 0L || _dateTo.value > 0L ||
        _amountMin.value != null || _amountMax.value != null

    val transactions = repo.observeAll().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val transactionsWithCategory = repo.observeAllWithCategory().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val textFiltered = combine(transactionsWithCategory, _searchQuery) { list, query ->
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

    val searchedTransactions = combine(
        textFiltered, _dateFrom, _dateTo, _amountMin, _amountMax
    ) { list: List<TransactionWithCategory>, df: Long, dt: Long, amin: Double?, amax: Double? ->
        list.filter { (tx, _) ->
            (df <= 0L || tx.date >= df) &&
            (dt <= 0L || tx.date <= dt) &&
            (amin == null || kotlin.math.abs(tx.amount) >= amin) &&
            (amax == null || kotlin.math.abs(tx.amount) <= amax)
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    suspend fun suggestCategoryId(description: String, amount: Double, type: TransactionType): Long? =
        repo.suggestCategoryId(description, amount, type)

    fun save(tx: Transaction) = viewModelScope.launch { repo.save(tx) }
    fun delete(tx: Transaction) = viewModelScope.launch { repo.delete(tx) }
}
