package de.mybudgets.app.viewmodel

import de.mybudgets.app.util.AppLogger
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
        AppLogger.e(TAG, "========== validateDrop CALLED ==========")
        AppLogger.e(TAG, "validateDrop: source=${source.name}(id=${source.id}, L${source.level})")
        AppLogger.e(TAG, "validateDrop: target=${target?.name}(id=${target?.id}, L${target?.level})")

        // 1. Self-drop
        if (source.id == target?.id) {
            AppLogger.e(TAG, "validateDrop: ❌ INVALID - Self-drop")
            return DropResult.Invalid("Kategorie kann nicht auf sich selbst verschoben werden")
        }

        // 2. Already at target (parent unchanged)
        if (source.parentCategoryId == target?.id) {
            AppLogger.e(TAG, "validateDrop: ❌ INVALID - Already child of target")
            return DropResult.Invalid("Kategorie ist bereits hier")
        }

        // 3. Circular reference (target ist Kind/Enkel von source)
        if (target != null && isDescendantOf(target, source)) {
            AppLogger.e(TAG, "validateDrop: ❌ INVALID - Circular reference")
            return DropResult.Invalid("Zirkuläre Referenz verhindert")
        }

        // 4. Calculate new level
        val newLevel = if (target == null) 1 else target.level + 1
        AppLogger.e(TAG, "validateDrop: newLevel=$newLevel")

        // 5. Max depth check
        if (newLevel > 3) {
            AppLogger.e(TAG, "validateDrop: ❌ INVALID - Max depth exceeded (newLevel=$newLevel > 3)")
            return DropResult.Invalid("Maximale Tiefe (Level 3) erreicht")
        }

        // 6. Children depth overflow
        val maxChildDepth = getMaxDescendantDepth(source)
        AppLogger.e(TAG, "validateDrop: maxChildDepth=$maxChildDepth, newLevel+maxChildDepth=${newLevel + maxChildDepth}")
        if (newLevel + maxChildDepth > 3) {
            AppLogger.e(TAG, "validateDrop: ❌ INVALID - Children would be too deep")
            return DropResult.Invalid("Unterkategorien würden zu tief (> Level 3)")
        }

        // 7. Warning for max depth (Level 3)
        if (newLevel == 3) {
            AppLogger.e(TAG, "validateDrop: ⚠️ WARNING - Level 3 (Maximum)")
            return DropResult.Warning(
                "Wird Level 3 (Maximum)",
                newLevel,
                target?.name
            )
        }

        // 8. Valid drop
        AppLogger.e(TAG, "validateDrop: ✅ VALID - newLevel=$newLevel")
        AppLogger.e(TAG, "========== validateDrop END ==========")
        return DropResult.Valid(newLevel, target?.name)
    }

    fun moveCategory(source: Category, newParentId: Long?) = viewModelScope.launch {
        AppLogger.e(TAG, "========== moveCategory CALLED ==========")
        AppLogger.e(TAG, "moveCategory: source=${source.name}(id=${source.id}, L${source.level}, parent=${source.parentCategoryId})")
        AppLogger.e(TAG, "moveCategory: newParentId=$newParentId")
        AppLogger.e(TAG, "moveCategory: Calling repo.moveCategory()...")
        
        repo.moveCategory(source, newParentId)
        
        AppLogger.e(TAG, "moveCategory: repo.moveCategory() returned, waiting 200ms for DB...")
        kotlinx.coroutines.delay(200)
        
        AppLogger.e(TAG, "moveCategory: Checking all categories from Flow...")
        val allCats = categories.value
        AppLogger.e(TAG, "moveCategory: Total categories in Flow: ${allCats.size}")
        allCats.forEach { cat ->
            AppLogger.e(TAG, "  📁 ${cat.name} | id=${cat.id} | L${cat.level} | parent=${cat.parentCategoryId}")
        }
        
        // Find source in updated list
        val updatedSource = allCats.find { it.id == source.id }
        if (updatedSource != null) {
            AppLogger.e(TAG, "moveCategory: ✅ Found source in Flow:")
            AppLogger.e(TAG, "  BEFORE: ${source.name} | L${source.level} | parent=${source.parentCategoryId}")
            AppLogger.e(TAG, "  AFTER:  ${updatedSource.name} | L${updatedSource.level} | parent=${updatedSource.parentCategoryId}")
            if (updatedSource.parentCategoryId == newParentId) {
                AppLogger.e(TAG, "moveCategory: ✅✅✅ UPDATE SUCCESSFUL!")
            } else {
                AppLogger.e(TAG, "moveCategory: ❌❌❌ UPDATE FAILED - parentId not changed!")
            }
        } else {
            AppLogger.e(TAG, "moveCategory: ❌ Source not found in Flow!")
        }
        
        AppLogger.e(TAG, "========== moveCategory END ==========")
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
