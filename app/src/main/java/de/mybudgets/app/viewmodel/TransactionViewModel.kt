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
    private val _selectedCategoryIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _showUncategorizedOnly = MutableStateFlow(false)

    val dateFrom: StateFlow<Long> = _dateFrom
    val dateTo: StateFlow<Long> = _dateTo
    val amountMin: StateFlow<Double?> = _amountMin
    val amountMax: StateFlow<Double?> = _amountMax
    val selectedCategoryIds: StateFlow<Set<Long>> = _selectedCategoryIds
    val showUncategorizedOnly: StateFlow<Boolean> = _showUncategorizedOnly

    fun setDateFrom(millis: Long) { _dateFrom.value = millis }
    fun setDateTo(millis: Long) { _dateTo.value = millis }
    fun setAmountMin(v: Double?) { _amountMin.value = v }
    fun setAmountMax(v: Double?) { _amountMax.value = v }
    fun setSelectedCategories(ids: Set<Long>) { _selectedCategoryIds.value = ids }
    fun setShowUncategorizedOnly(show: Boolean) { _showUncategorizedOnly.value = show }
    fun toggleCategory(id: Long) {
        val current = _selectedCategoryIds.value.toMutableSet()
        if (current.contains(id)) current.remove(id) else current.add(id)
        _selectedCategoryIds.value = current
    }

    fun clearFilters() {
        _searchQuery.value = ""
        _dateFrom.value = 0L
        _dateTo.value = 0L
        _amountMin.value = null
        _amountMax.value = null
        _selectedCategoryIds.value = emptySet()
        _showUncategorizedOnly.value = false
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
        textFiltered, _dateFrom, _dateTo, _amountMin, _amountMax, _selectedCategoryIds, _showUncategorizedOnly, transactionsWithCategory
    ) { flows: Array<*> ->
        @Suppress("UNCHECKED_CAST")
        val list = flows[0] as List<TransactionWithCategory>
        val df = flows[1] as Long
        val dt = flows[2] as Long
        val amin = flows[3] as Double?
        val amax = flows[4] as Double?
        val catIds = flows[5] as Set<Long>
        val uncatOnly = flows[6] as Boolean
        val allTxWithCat = flows[7] as List<TransactionWithCategory>
        
        // Build set of all categories and their children
        val allCategories = allTxWithCat.mapNotNull { it.category }.distinctBy { it.id }
        val selectedCatsWithChildren = mutableSetOf<Long>()
        catIds.forEach { catId ->
            selectedCatsWithChildren.add(catId)
            selectedCatsWithChildren.addAll(getChildrenRecursive(catId, allCategories))
        }
        
        list.filter { (tx, cat) ->
            val dateOk = (df <= 0L || tx.date >= df) && (dt <= 0L || tx.date <= dt)
            val amountOk = (amin == null || kotlin.math.abs(tx.amount) >= amin) && (amax == null || kotlin.math.abs(tx.amount) <= amax)
            val categoryOk = when {
                uncatOnly -> tx.categoryId == null
                catIds.isEmpty() -> true
                else -> tx.categoryId in selectedCatsWithChildren
            }
            dateOk && amountOk && categoryOk
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun exportFilteredAsCsv(): String {
        val list = searchedTransactions.value
        val sb = StringBuilder()
        sb.appendLine("Datum;Beschreibung;Betrag;Typ;Kategorie;Notiz")
        val fmt = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.GERMANY)
        for ((tx, cat) in list) {
            val date = fmt.format(java.util.Date(tx.date))
            val desc = tx.description.replace(";", ",")
            val amount = "%.2f €".format(tx.amount).replace(".", ",")
            val type = when (tx.type) {
                TransactionType.INCOME -> "Einnahme"
                TransactionType.EXPENSE -> "Ausgabe"
                TransactionType.TRANSFER -> "Transfer"
            }
            val category = cat?.name?.replace(";", ",") ?: ""
            val note = tx.note.replace(";", ",")
            sb.appendLine("$date;$desc;$amount;$type;$category;$note")
        }
        return sb.toString()
    }

    suspend fun suggestCategoryId(description: String, amount: Double, type: TransactionType): Long? =
        repo.suggestCategoryId(description, amount, type)

    fun save(tx: Transaction) = viewModelScope.launch { repo.save(tx) }
    fun delete(tx: Transaction) = viewModelScope.launch { repo.delete(tx) }
    
    private fun getChildrenRecursive(parentId: Long, allCategories: List<de.mybudgets.app.data.model.Category>): List<Long> {
        val result = mutableListOf<Long>()
        val children = allCategories.filter { it.parentCategoryId == parentId }
        for (child in children) {
            result.add(child.id)
            result.addAll(getChildrenRecursive(child.id, allCategories))
        }
        return result
    }
}
