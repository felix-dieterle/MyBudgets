package de.mybudgets.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.mybudgets.app.data.repository.AccountRepository
import de.mybudgets.app.data.repository.CategoryRepository
import de.mybudgets.app.data.repository.TransactionRepository
import de.mybudgets.app.util.DashboardInsights
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val accountRepo: AccountRepository,
    private val txRepo: TransactionRepository,
    private val categoryRepo: CategoryRepository
) : ViewModel() {

    val totalBalance = accountRepo.observeTotalBalance().stateIn(viewModelScope, SharingStarted.Lazily, 0.0)
    val accounts     = accountRepo.observeAll().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val transactions = txRepo.observeAll().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val recentTransactions = txRepo.observeAll()
        .map { it.take(5) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val virtualOverview = combine(accounts, transactions) { accs, txs ->
        DashboardInsights.buildVirtualOverview(accs, txs)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val trendSummary = transactions
        .map { DashboardInsights.buildTrendSummary(System.currentTimeMillis(), it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, "")

    val predictionWarnings = combine(
        accounts,
        transactions,
        categoryRepo.observeAll()
    ) { accs, txs, categories ->
        DashboardInsights.buildPredictionWarnings(System.currentTimeMillis(), accs, categories, txs)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}
