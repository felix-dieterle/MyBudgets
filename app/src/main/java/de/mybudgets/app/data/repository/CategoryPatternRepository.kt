package de.mybudgets.app.data.repository

import de.mybudgets.app.data.db.CategoryPatternDao
import de.mybudgets.app.data.model.CategoryPattern
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryPatternRepository @Inject constructor(
    private val dao: CategoryPatternDao
) {
    fun observeByCategory(categoryId: Long): Flow<List<CategoryPattern>> = dao.observeByCategory(categoryId)

    suspend fun getAll(): List<CategoryPattern> = dao.getAll()
    suspend fun getAllActive(): List<CategoryPattern> = dao.getAllActive()
    suspend fun getById(id: Long): CategoryPattern? = dao.getById(id)
    suspend fun save(pattern: CategoryPattern): Long =
        if (pattern.id == 0L) dao.insert(pattern) else { dao.update(pattern); pattern.id }

    suspend fun delete(pattern: CategoryPattern) = dao.delete(pattern)

    suspend fun findTextMatch(usageText: String): CategoryPattern? = dao.findTextMatch(usageText)

    suspend fun incrementUsage(id: Long) = dao.incrementUsage(id)
}
