package de.mybudgets.app.data.db

import androidx.room.*
import de.mybudgets.app.data.model.Label
import de.mybudgets.app.data.model.TransactionLabel
import kotlinx.coroutines.flow.Flow

@Dao
abstract class LabelDao {
    @Query("SELECT * FROM labels ORDER BY name")
    abstract fun observeAll(): Flow<List<Label>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insert(label: Label): Long

    @Update
    abstract suspend fun update(label: Label)

    @Delete
    abstract suspend fun delete(label: Label)

    @Query("SELECT * FROM labels WHERE name = :name LIMIT 1")
    abstract suspend fun findByName(name: String): Label?

    /**
     * Step 1: Copy transaction_labels entries that point to a duplicate label so they
     * instead point to the kept (lowest-id) label with the same name.
     * OR IGNORE skips the copy when the transaction is already linked to the kept label.
     */
    @Query("""
        INSERT OR IGNORE INTO transaction_labels (transactionId, labelId)
        SELECT tl.transactionId,
               (SELECT MIN(l2.id) FROM labels l2 WHERE l2.name = l.name)
        FROM transaction_labels tl
        JOIN labels l ON l.id = tl.labelId
        WHERE tl.labelId NOT IN (SELECT MIN(id) FROM labels GROUP BY name)
    """)
    protected abstract suspend fun reassignTransactionLabels()

    /**
     * Step 2: Remove the now-superseded transaction_labels entries that still point
     * to a duplicate (non-kept) label ID.
     */
    @Query("""
        DELETE FROM transaction_labels
        WHERE labelId NOT IN (SELECT MIN(id) FROM labels GROUP BY name)
    """)
    protected abstract suspend fun deleteOrphanedTransactionLabels()

    /**
     * Step 3: Remove duplicate label rows, keeping the one with the lowest id per name.
     */
    @Query("DELETE FROM labels WHERE id NOT IN (SELECT MIN(id) FROM labels GROUP BY name)")
    protected abstract suspend fun deleteDuplicateLabels()

    /**
     * Removes duplicate label rows that share the same name while preserving all
     * transaction-label associations (they are re-linked to the kept label first).
     * Safe to call on every app start.
     */
    @Transaction
    open suspend fun deleteDuplicates() {
        reassignTransactionLabels()
        deleteOrphanedTransactionLabels()
        deleteDuplicateLabels()
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun addLabelToTransaction(link: TransactionLabel)

    @Delete
    abstract suspend fun removeLabelFromTransaction(link: TransactionLabel)

    @Query("SELECT l.* FROM labels l INNER JOIN transaction_labels tl ON l.id = tl.labelId WHERE tl.transactionId = :transactionId")
    abstract suspend fun getLabelsForTransaction(transactionId: Long): List<Label>
}
