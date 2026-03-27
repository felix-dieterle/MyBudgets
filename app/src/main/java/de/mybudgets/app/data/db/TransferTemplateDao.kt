package de.mybudgets.app.data.db

import androidx.room.*
import de.mybudgets.app.data.model.TransferTemplate
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferTemplateDao {

    @Query("SELECT * FROM transfer_templates ORDER BY name ASC")
    fun observeAll(): Flow<List<TransferTemplate>>

    @Query("SELECT * FROM transfer_templates WHERE id = :id")
    suspend fun getById(id: Long): TransferTemplate?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(template: TransferTemplate): Long

    @Update
    suspend fun update(template: TransferTemplate)

    @Delete
    suspend fun delete(template: TransferTemplate)
}
