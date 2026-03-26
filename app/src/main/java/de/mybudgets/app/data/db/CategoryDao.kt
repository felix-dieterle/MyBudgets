package de.mybudgets.app.data.db

import androidx.room.*
import de.mybudgets.app.data.model.Category
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY level, name")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE level = 1 ORDER BY name")
    suspend fun getTopLevel(): List<Category>

    @Query("SELECT * FROM categories WHERE parentCategoryId = :parentId ORDER BY name")
    suspend fun getChildren(parentId: Long): List<Category>

    @Query("SELECT * FROM categories WHERE pattern != '' ORDER BY level DESC")
    suspend fun getWithPatterns(): List<Category>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: Category): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<Category>)

    @Update
    suspend fun update(category: Category)

    @Delete
    suspend fun delete(category: Category)
}
