package de.mybudgets.app.data.repository

import de.mybudgets.app.util.AppLogger
import androidx.room.withTransaction
import de.mybudgets.app.data.db.AppDatabase
import de.mybudgets.app.data.db.CategoryDao
import de.mybudgets.app.data.model.Category
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(
    private val dao: CategoryDao,
    private val db: AppDatabase
) {
    fun observeAll(): Flow<List<Category>> = dao.observeAll()
    suspend fun getTopLevel(): List<Category> = dao.getTopLevel()
    suspend fun getChildren(parentId: Long): List<Category> = dao.getChildren(parentId)
    suspend fun getWithPatterns(): List<Category> = dao.getWithPatterns()
    suspend fun save(category: Category): Long = if (category.id == 0L) dao.insert(category) else { dao.update(category); category.id }
    suspend fun insertAll(categories: List<Category>) = dao.insertAll(categories)
    suspend fun hasDefaultCategories(): Boolean = dao.countDefaults() > 0
    suspend fun delete(category: Category) = dao.delete(category)

    suspend fun moveCategory(source: Category, newParentId: Long?) = db.withTransaction {
        AppLogger.e(TAG, "========== moveCategory (Repository) CALLED ==========")
        AppLogger.e(TAG, "moveCategory: source=${source.name}(id=${source.id}, currentLevel=${source.level}, currentParent=${source.parentCategoryId})")
        AppLogger.e(TAG, "moveCategory: newParentId=$newParentId")

        // Calculate new level
        val newLevel = if (newParentId == null) {
            AppLogger.e(TAG, "moveCategory: Target is null → newLevel=1 (Top-Level)")
            1
        } else {
            val parent = dao.getById(newParentId)
            if (parent == null) {
                AppLogger.e(TAG, "moveCategory: ❌ ERROR - Parent not found (id=$newParentId)")
                return@withTransaction
            }
            AppLogger.e(TAG, "moveCategory: Parent found: ${parent.name}(L${parent.level}) → newLevel=${parent.level + 1}")
            parent.level + 1
        }

        AppLogger.e(TAG, "moveCategory: Creating updated category...")
        val updatedCategory = source.copy(
            parentCategoryId = newParentId,
            level = newLevel
        )
        AppLogger.e(TAG, "moveCategory: Updated category: ${updatedCategory.name}(id=${updatedCategory.id}, L${updatedCategory.level}, parent=${updatedCategory.parentCategoryId})")
        
        AppLogger.e(TAG, "moveCategory: Calling dao.update()...")
        val rowsAffected = dao.update(updatedCategory)
        AppLogger.e(TAG, "moveCategory: dao.update() returned rowsAffected=$rowsAffected")

        if (rowsAffected == 0) {
            AppLogger.e(TAG, "moveCategory: ❌❌❌ FAILED - dao.update() returned 0 rows!")
            return@withTransaction
        } else {
            AppLogger.e(TAG, "moveCategory: ✅ dao.update() successful")
        }

        // Update all descendants (cascade level changes)
        val levelDelta = newLevel - source.level
        AppLogger.e(TAG, "moveCategory: levelDelta=$levelDelta (newLevel=$newLevel - oldLevel=${source.level})")
        if (levelDelta != 0) {
            AppLogger.e(TAG, "moveCategory: Cascading level changes to descendants...")
            updateDescendantsLevel(source.id, levelDelta)
        } else {
            AppLogger.e(TAG, "moveCategory: No level change, skipping descendant updates")
        }

        AppLogger.e(TAG, "moveCategory: ✅✅✅ Transaction complete")
        AppLogger.e(TAG, "========== moveCategory (Repository) END ==========")
    }

    private suspend fun updateDescendantsLevel(parentId: Long, levelDelta: Int) {
        val children = dao.getChildren(parentId)
        AppLogger.e(TAG, "updateDescendantsLevel: parentId=$parentId, levelDelta=$levelDelta, children=${children.size}")
        children.forEach { child ->
            AppLogger.e(TAG, "updateDescendantsLevel: Updating ${child.name} from L${child.level} to L${child.level + levelDelta}")
            val updated = dao.update(child.copy(level = child.level + levelDelta))
            AppLogger.e(TAG, "updateDescendantsLevel: ${child.name} rowsAffected=$updated")
            // Recursively update grandchildren
            updateDescendantsLevel(child.id, levelDelta)
        }
    }

    companion object {
        private const val TAG = "CategoryRepository"
    }
}
