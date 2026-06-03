package de.mybudgets.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import de.mybudgets.app.data.model.CategoryPattern
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryPatternDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pattern: CategoryPattern): Long

    @Update
    suspend fun update(pattern: CategoryPattern)

    @Delete
    suspend fun delete(pattern: CategoryPattern)

    @Query("SELECT * FROM category_patterns WHERE id = :id")
    suspend fun getById(id: Long): CategoryPattern?

    @Query("SELECT * FROM category_patterns WHERE categoryId = :categoryId")
    fun observeByCategory(categoryId: Long): Flow<List<CategoryPattern>>

    @Query("SELECT * FROM category_patterns ORDER BY usageCount DESC, lastUsed DESC")
    suspend fun getAll(): List<CategoryPattern>

    @Query("SELECT * FROM category_patterns WHERE confidence >= 0.7 ORDER BY confidence DESC, usageCount DESC")
    suspend fun getAllActive(): List<CategoryPattern>

    @Query("SELECT * FROM category_patterns WHERE patternType = 'IBAN' AND patternValue = :iban")
    suspend fun getByIban(iban: String): CategoryPattern?

    @Query("SELECT * FROM category_patterns WHERE patternType = :type ORDER BY confidence DESC, usageCount DESC")
    suspend fun getAllByType(type: String): List<CategoryPattern>

    @Query("UPDATE category_patterns SET usageCount = usageCount + 1, lastUsed = :timestamp WHERE id = :id")
    suspend fun incrementUsage(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE category_patterns SET confidence = :newConfidence WHERE id = :id")
    suspend fun updateConfidence(id: Long, newConfidence: Double)

    @Query("DELETE FROM category_patterns WHERE usageCount = 0 AND createdAt < :olderThan")
    suspend fun deleteUnused(olderThan: Long)
}
