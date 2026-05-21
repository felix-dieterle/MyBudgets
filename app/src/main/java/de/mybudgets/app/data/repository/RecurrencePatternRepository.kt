package de.mybudgets.app.data.repository

import de.mybudgets.app.data.db.RecurrencePatternDao
import de.mybudgets.app.data.model.RecurrencePattern
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecurrencePatternRepository @Inject constructor(
    private val dao: RecurrencePatternDao
) {
    
    val patterns: Flow<List<RecurrencePattern>> = dao.getAll()
    
    suspend fun getById(id: Long): RecurrencePattern? = dao.getById(id)
    
    suspend fun save(pattern: RecurrencePattern): Long {
        return if (pattern.id == 0L) {
            dao.insert(pattern)
        } else {
            dao.update(pattern)
            pattern.id
        }
    }
    
    suspend fun delete(pattern: RecurrencePattern) = dao.delete(pattern)
    
    suspend fun deleteById(id: Long) = dao.deleteById(id)
    
    suspend fun markUsed(id: Long) = dao.updateLastUsed(id, System.currentTimeMillis())
}
