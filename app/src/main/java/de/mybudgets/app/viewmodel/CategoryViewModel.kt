package de.mybudgets.app.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.mybudgets.app.data.model.Category
import de.mybudgets.app.data.model.DropResult
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

    fun validateDrop(source: Category, target: Category?): DropResult {
        Log.d(TAG, "validateDrop: source=${source.name}(L${source.level}), target=${target?.name}(L${target?.level})")

        // 1. Self-drop
        if (source.id == target?.id) {
            Log.d(TAG, "validateDrop: INVALID - Self-drop")
            return DropResult.Invalid("Kategorie kann nicht auf sich selbst verschoben werden")
        }

        // 2. Circular reference (target ist Kind/Enkel von source)
        if (target != null && isDescendantOf(target, source)) {
            Log.d(TAG, "validateDrop: INVALID - Circular reference")
            return DropResult.Invalid("Zirkuläre Referenz verhindert")
        }

        // 3. Calculate new level
        val newLevel = if (target == null) 1 else target.level + 1
        Log.d(TAG, "validateDrop: newLevel=$newLevel")

        // 4. Max depth check
        if (newLevel > 3) {
            Log.d(TAG, "validateDrop: INVALID - Max depth exceeded")
            return DropResult.Invalid("Maximale Tiefe (Level 3) erreicht")
        }

        // 5. Children depth overflow
        val maxChildDepth = getMaxDescendantDepth(source)
        Log.d(TAG, "validateDrop: maxChildDepth=$maxChildDepth")
        if (newLevel + maxChildDepth > 3) {
            Log.d(TAG, "validateDrop: INVALID - Children would be too deep")
            return DropResult.Invalid("Unterkategorien würden zu tief (> Level 3)")
        }

        // 6. Warning for max depth (Level 3)
        if (newLevel == 3) {
            Log.d(TAG, "validateDrop: WARNING - Level 3")
            return DropResult.Warning(
                "Wird Level 3 (Maximum)",
                newLevel,
                target?.name
            )
        }

        // 7. Valid drop
        Log.d(TAG, "validateDrop: VALID")
        return DropResult.Valid(newLevel, target?.name)
    }

    fun moveCategory(source: Category, newParentId: Long?) = viewModelScope.launch {
        Log.d(TAG, "moveCategory: ${source.name} -> parentId=$newParentId")
        repo.moveCategory(source, newParentId)
        Log.d(TAG, "moveCategory: Done")
    }

    private fun isDescendantOf(child: Category, ancestor: Category): Boolean {
        val allCategories = categories.value
        var current = child
        while (current.parentCategoryId != null) {
            current = allCategories.find { it.id == current.parentCategoryId } ?: break
            if (current.id == ancestor.id) return true
        }
        return false
    }

    private fun getMaxDescendantDepth(category: Category): Int {
        val allCategories = categories.value
        val children = allCategories.filter { it.parentCategoryId == category.id }
        if (children.isEmpty()) return 0

        return children.maxOfOrNull { child ->
            1 + getMaxDescendantDepth(child)
        } ?: 0
    }

    companion object {
        private const val TAG = "CategoryViewModel"
    }
}
