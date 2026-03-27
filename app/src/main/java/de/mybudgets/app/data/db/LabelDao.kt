package de.mybudgets.app.data.db

import androidx.room.*
import de.mybudgets.app.data.model.Label
import de.mybudgets.app.data.model.TransactionLabel
import kotlinx.coroutines.flow.Flow

@Dao
interface LabelDao {
    @Query("SELECT * FROM labels ORDER BY name")
    fun observeAll(): Flow<List<Label>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(label: Label): Long

    @Update
    suspend fun update(label: Label)

    @Delete
    suspend fun delete(label: Label)

    @Query("DELETE FROM labels WHERE id NOT IN (SELECT MIN(id) FROM labels GROUP BY name)")
    suspend fun deleteDuplicates()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addLabelToTransaction(link: TransactionLabel)

    @Delete
    suspend fun removeLabelFromTransaction(link: TransactionLabel)

    @Query("SELECT l.* FROM labels l INNER JOIN transaction_labels tl ON l.id = tl.labelId WHERE tl.transactionId = :transactionId")
    suspend fun getLabelsForTransaction(transactionId: Long): List<Label>
}
