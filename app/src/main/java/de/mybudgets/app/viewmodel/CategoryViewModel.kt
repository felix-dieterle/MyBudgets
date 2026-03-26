package de.mybudgets.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.mybudgets.app.data.model.Category
import de.mybudgets.app.data.repository.CategoryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CategoryViewModel @Inject constructor(
    private val repo: CategoryRepository
) : ViewModel() {

    val categories = repo.observeAll().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun save(cat: Category) = viewModelScope.launch { repo.save(cat) }
    fun delete(cat: Category) = viewModelScope.launch { repo.delete(cat) }
}
