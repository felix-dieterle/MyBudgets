package de.mybudgets.app.data.repository

import android.util.Log
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
        Log.d(TAG, "moveCategory: ${source.name} (id=${source.id}, currentLevel=${source.level}) -> parentId=$newParentId")

        // Calculate new level
        val newLevel = if (newParentId == null) {
            1
        } else {
            val parent = dao.getById(newParentId)
            if (parent == null) {
                Log.e(TAG, "moveCategory: Parent not found (id=$newParentId)")
                return@withTransaction
            }
            parent.level + 1
        }

        Log.d(TAG, "moveCategory: newLevel=$newLevel")

        // Update source category
        val updated = dao.update(source.copy(
            parentCategoryId = newParentId,
            level = newLevel
        ))
        Log.d(TAG, "moveCategory: Updated source category (rowsAffected=$updated)")

        if (updated == 0) {
            Log.e(TAG, "moveCategory: Failed to update source category!")
            return@withTransaction
        }

        // Update all descendants (cascade level changes)
        val levelDelta = newLevel - source.level
        Log.d(TAG, "moveCategory: levelDelta=$levelDelta")
        if (levelDelta != 0) {
            updateDescendantsLevel(source.id, levelDelta)
        }

        Log.d(TAG, "moveCategory: Complete")
    }

    private suspend fun updateDescendantsLevel(parentId: Long, levelDelta: Int) {
        val children = dao.getChildren(parentId)
        Log.d(TAG, "updateDescendantsLevel: parentId=$parentId, levelDelta=$levelDelta, children=${children.size}")
        children.forEach { child ->
            val updated = dao.update(child.copy(level = child.level + levelDelta))
            Log.d(TAG, "updateDescendantsLevel: Updated ${child.name} to level ${child.level + levelDelta} (rowsAffected=$updated)")
            // Recursively update grandchildren
            updateDescendantsLevel(child.id, levelDelta)
        }
    }

    companion object {
        private const val TAG = "CategoryRepository"
    }
}
