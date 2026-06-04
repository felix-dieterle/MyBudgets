package de.mybudgets.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.mybudgets.app.data.repository.${FEATURE_PASCAL}Repository
import de.mybudgets.app.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "${FEATURE_PASCAL}ViewModel"

/**
 * ViewModel for ${FEATURE} feature.
 *
 * **State Management Pattern:**
 * - Private MutableStateFlow (mutable inside ViewModel only)
 * - Public StateFlow (read-only to Fragment)
 * - Sealed State class with 4 states: Idle | Loading | Success(data) | Error(msg)
 * - All mutations via ViewModel methods (never direct from Fragment)
 *
 * **Scope & Lifecycle:**
 * - viewModelScope: Automatically cancels coroutines when ViewModel cleared
 * - Survives configuration changes (rotation)
 * - Cleared when Fragment is destroyed
 *
 * **Example States:**
 * ```
 * Idle → user clicks → Loading(msg="Loading...")
 * Loading → repo returns data → Success(data)
 * Loading → repo throws error → Error(msg="Something went wrong")
 * Success/Error → user retries → back to Loading
 * ```
 */
@HiltViewModel
class ${FEATURE_PASCAL}ViewModel @Inject constructor(
    private val repository: ${FEATURE_PASCAL}Repository
) : ViewModel() {
    
    // Private mutable state (only modified inside ViewModel)
    private val _state = MutableStateFlow<${FEATURE_PASCAL}State>(${FEATURE_PASCAL}State.Idle)
    
    // Public read-only state (Fragment observes this)
    val state: StateFlow<${FEATURE_PASCAL}State> = _state.asStateFlow()
    
    // TODO: Add additional flows as needed
    // private val _items = MutableStateFlow<List<${FEATURE_PASCAL}>>(emptyList())
    // val items: StateFlow<List<${FEATURE_PASCAL}>> = _items.asStateFlow()

    init {
        AppLogger.d(TAG, "ViewModel initialized")
        // TODO: Load initial data if needed
        // loadItems()
    }

    /**
     * Main action: Load/fetch data from repository.
     *
     * Flow:
     * 1. Emit Loading state
     * 2. Call repository (suspending)
     * 3. Emit Success(data) or Error(msg)
     */
    fun loadData() {
        AppLogger.i(TAG, "loadData() called")
        _state.value = ${FEATURE_PASCAL}State.Loading("Loading ${FEATURE}...")
        
        viewModelScope.launch {
            try {
                val data = repository.fetch${FEATURE_PASCAL}()
                AppLogger.i(TAG, "loadData() success - ${data.size} items")
                _state.value = ${FEATURE_PASCAL}State.Success(data)
            } catch (e: Exception) {
                AppLogger.e(TAG, "loadData() failed", e)
                _state.value = ${FEATURE_PASCAL}State.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Retry loading (when user taps retry button).
     */
    fun retry() {
        AppLogger.i(TAG, "retry() called")
        loadData()
    }

    // TODO: Add more methods for user actions
    // fun save(item: ${FEATURE_PASCAL}) { ... }
    // fun delete(id: Long) { ... }
    // fun update(id: Long, name: String) { ... }
}

/**
 * State sealed class for ${FEATURE} screen.
 *
 * Usage: Fragment uses when() to handle each state
 * ```
 * when (state) {
 *     is ${FEATURE_PASCAL}State.Idle -> showIdle()
 *     is ${FEATURE_PASCAL}State.Loading -> showLoading(state.message)
 *     is ${FEATURE_PASCAL}State.Success -> showData(state.data)
 *     is ${FEATURE_PASCAL}State.Error -> showError(state.message)
 * }
 * ```
 */
sealed class ${FEATURE_PASCAL}State {
    /**
     * Initial state (no action performed).
     */
    object Idle : ${FEATURE_PASCAL}State()
    
    /**
     * Loading state (repository call in progress).
     * @param message Optional loading message (e.g., "Loading transactions...")
     */
    data class Loading(val message: String = "") : ${FEATURE_PASCAL}State()
    
    /**
     * Success state (data loaded).
     * @param data Successfully loaded data
     */
    data class Success(val data: List<${FEATURE_PASCAL}>) : ${FEATURE_PASCAL}State()
    
    /**
     * Error state (operation failed).
     * @param message Error message for user display
     */
    data class Error(val message: String) : ${FEATURE_PASCAL}State()
}
