package de.mybudgets.app.data.db

import androidx.room.*
import de.mybudgets.app.data.model.Transaction
import de.mybudgets.app.data.model.TransactionWithCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun observeAll(): Flow<List<Transaction>>

    @androidx.room.Transaction
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun observeAllWithCategory(): Flow<List<TransactionWithCategory>>

    @Query("SELECT * FROM transactions WHERE accountId = :accountId ORDER BY date DESC")
    fun observeByAccount(accountId: Long): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE categoryId = :categoryId ORDER BY date DESC")
    fun observeByCategory(categoryId: Long): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE date BETWEEN :from AND :to ORDER BY date DESC")
    fun observeByDateRange(from: Long, to: Long): Flow<List<Transaction>>

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int

    @Query("SELECT * FROM transactions ORDER BY date DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<Transaction>

    @Query("SELECT * FROM transactions WHERE remoteId IS NULL ORDER BY date ASC")
    suspend fun getUnsynced(): List<Transaction>

    @Query("SELECT remoteId FROM transactions WHERE remoteId IS NOT NULL")
    suspend fun getAllRemoteIds(): List<String>

    /**
     * Setzt die remoteId der uebergebenen Buchungen zurueck, damit sie beim naechsten Sync
     * erneut gesendet werden. Nur fuer "verwaiste" IDs gedacht: der Nutzer loest das aus,
     * wenn das Backend die Buchung nicht (mehr) kennt und die Zaehler sonst dauerhaft auseinander bleiben.
     */
    @Query("UPDATE transactions SET remoteId = NULL WHERE remoteId IN (:remoteIds)")
    suspend fun clearRemoteIds(remoteIds: List<String>): Int

    /** Reactiv: emittiert neu, sobald eine Buchung hochgeladen oder geloescht wurde. */
    @Query("SELECT COUNT(*) FROM transactions WHERE remoteId IS NULL")
    fun observeUnsyncedCount(): Flow<Int>

    /** Reaktiv: Gesamtzahl lokaler Buchungen - Grundlage fuer den Vergleich mit dem Backend. */
    @Query("SELECT COUNT(*) FROM transactions")
    fun observeCount(): Flow<Int>

    @Query("SELECT MAX(date) FROM transactions WHERE accountId = :accountId")
    suspend fun getLatestDateForAccount(accountId: Long): Long?

    @Query("SELECT MIN(date) FROM transactions WHERE accountId = :accountId")
    suspend fun getEarliestDateForAccount(accountId: Long): Long?

    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :accountId")
    suspend fun getTransactionCountForAccount(accountId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: Transaction): Long

    @Update
    suspend fun update(transaction: Transaction)

    @Query("UPDATE transactions SET isRecurring = 1, recurringIntervalDays = :intervalDays WHERE id IN (:ids)")
    suspend fun markAsRecurring(ids: List<Long>, intervalDays: Int)

    @Query("SELECT * FROM transactions")
    suspend fun getAll(): List<Transaction>

    /**
     * Setzt Kategorie und Override-Herkunft fuer die uebergebenen Buchungen (US-004).
     * Nur fuer Buchungen ohne Kategorie gedacht - die Auswahl trifft der Aufrufer.
     */
    @Query("UPDATE transactions SET categoryId = :categoryId, patternOverrideKey = :key WHERE id IN (:ids)")
    suspend fun applyPatternOverride(ids: List<Long>, categoryId: Long, key: String): Int

    /** Nimmt einen angewendeten Override zurueck: Kategorie und Herkunft werden geleert. */
    @Query("UPDATE transactions SET categoryId = NULL, patternOverrideKey = NULL WHERE patternOverrideKey = :key")
    suspend fun resetPatternOverride(key: String): Int

    /** Anzahl der Buchungen, deren Kategorie aus genau diesem Override stammt. */
    @Query("SELECT COUNT(*) FROM transactions WHERE patternOverrideKey = :key")
    suspend fun countByPatternOverrideKey(key: String): Int

    @Delete
    suspend fun delete(transaction: Transaction)
    
    @Query("DELETE FROM transactions WHERE accountId = :accountId AND date >= :fromDate AND date <= :toDate")
    suspend fun deleteInDateRange(accountId: Long, fromDate: Long, toDate: Long): Int
}
