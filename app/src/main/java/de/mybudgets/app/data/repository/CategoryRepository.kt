package de.mybudgets.app.data.repository

import de.mybudgets.app.data.db.CategoryDao
import de.mybudgets.app.data.model.Category
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(
    private val dao: CategoryDao
) {
    fun observeAll(): Flow<List<Category>> = dao.observeAll()
    suspend fun getTopLevel(): List<Category> = dao.getTopLevel()
    suspend fun getChildren(parentId: Long): List<Category> = dao.getChildren(parentId)
    suspend fun getWithPatterns(): List<Category> = dao.getWithPatterns()
    suspend fun save(category: Category): Long = if (category.id == 0L) dao.insert(category) else { dao.update(category); category.id }
    suspend fun insertAll(categories: List<Category>) = dao.insertAll(categories)
    suspend fun delete(category: Category) = dao.delete(category)
}
