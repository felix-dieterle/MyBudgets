package de.mybudgets.app.data.db

import androidx.room.*
import de.mybudgets.app.data.model.RecurrencePattern
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurrencePatternDao {
    
    @Query("SELECT * FROM recurrence_patterns ORDER BY name ASC")
    fun getAll(): Flow<List<RecurrencePattern>>
    
    @Query("SELECT * FROM recurrence_patterns WHERE id = :id")
    suspend fun getById(id: Long): RecurrencePattern?
    
    @Insert
    suspend fun insert(pattern: RecurrencePattern): Long
    
    @Update
    suspend fun update(pattern: RecurrencePattern)
    
    @Delete
    suspend fun delete(pattern: RecurrencePattern)
    
    @Query("DELETE FROM recurrence_patterns WHERE id = :id")
    suspend fun deleteById(id: Long)
    
    @Query("UPDATE recurrence_patterns SET lastUsed = :timestamp WHERE id = :id")
    suspend fun updateLastUsed(id: Long, timestamp: Long)
}
