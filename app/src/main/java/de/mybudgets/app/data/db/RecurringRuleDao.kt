package de.mybudgets.app.data.db

import androidx.room.*
import de.mybudgets.app.data.model.RecurringRule
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringRuleDao {
    @Query("SELECT * FROM recurring_rules WHERE isActive = 1 ORDER BY name")
    fun observeActive(): Flow<List<RecurringRule>>

    @Query("SELECT * FROM recurring_rules WHERE isActive = 1 ORDER BY name")
    suspend fun getActive(): List<RecurringRule>

    @Query("SELECT * FROM recurring_rules WHERE id = :id")
    suspend fun getById(id: Long): RecurringRule?

    @Query("SELECT * FROM recurring_rules")
    fun observeAll(): Flow<List<RecurringRule>>

    @Insert
    suspend fun insert(rule: RecurringRule): Long

    @Update
    suspend fun update(rule: RecurringRule)

    @Query("UPDATE recurring_rules SET isActive = 0 WHERE id = :id")
    suspend fun deactivate(id: Long)

    @Delete
    suspend fun delete(rule: RecurringRule)
}
