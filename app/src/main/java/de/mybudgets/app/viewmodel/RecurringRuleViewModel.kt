package de.mybudgets.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.mybudgets.app.data.model.RecurringRule
import de.mybudgets.app.data.repository.RecurringRuleRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecurringRuleViewModel @Inject constructor(
    private val ruleRepo: RecurringRuleRepository
) : ViewModel() {

    val rules: StateFlow<List<RecurringRule>> =
        ruleRepo.observeAll().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun toggleActive(rule: RecurringRule) {
        viewModelScope.launch {
            ruleRepo.save(rule.copy(isActive = !rule.isActive))
        }
    }

    fun delete(rule: RecurringRule) {
        viewModelScope.launch {
            ruleRepo.delete(rule)
        }
    }
}
