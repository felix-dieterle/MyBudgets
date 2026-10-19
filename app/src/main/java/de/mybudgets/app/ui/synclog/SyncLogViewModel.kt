package de.mybudgets.app.ui.synclog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.mybudgets.app.data.model.SyncLogEntry
import de.mybudgets.app.data.repository.SyncLogRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SyncLogViewModel @Inject constructor(
    private val repo: SyncLogRepository
) : ViewModel() {

    val entries: StateFlow<List<SyncLogEntry>> = repo.entries
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun clear() {
        viewModelScope.launch { repo.clear() }
    }
}
