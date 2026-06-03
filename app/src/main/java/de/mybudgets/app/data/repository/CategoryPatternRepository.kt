package de.mybudgets.app.data.repository

import de.mybudgets.app.data.db.CategoryPatternDao
import de.mybudgets.app.data.model.CategoryPattern
import de.mybudgets.app.util.PatternMatcher
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

    /**
     * Findet das beste matching TEXT-Pattern für einen gegebenen Text.
     * Verwendet PatternMatcher für korrekte Keyword-Logik (AND, nicht LIKE).
     */
    suspend fun findTextMatch(description: String, note: String): CategoryPattern? {
        val allPatterns = dao.getAllByType("TEXT")
        return allPatterns
            .filter { PatternMatcher.matchTextPattern(it.patternValue, description, note) }
            .sortedWith(compareBy({ -it.confidence }, { -it.usageCount }))
            .firstOrNull()
    }

    suspend fun incrementUsage(id: Long) = dao.incrementUsage(id)
}

