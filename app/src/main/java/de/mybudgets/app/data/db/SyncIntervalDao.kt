package de.mybudgets.app.data.db

import androidx.room.*
import de.mybudgets.app.data.model.SyncInterval

@Dao
interface SyncIntervalDao {
    @Query("SELECT * FROM sync_intervals WHERE accountId = :accountId ORDER BY startDate ASC")
    suspend fun getAllForAccount(accountId: Long): List<SyncInterval>

    @Query("SELECT * FROM sync_intervals WHERE accountId = :accountId AND isHistorical = 1 ORDER BY startDate ASC")
    suspend fun getHistoricalForAccount(accountId: Long): List<SyncInterval>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(interval: SyncInterval): Long

    @Query("DELETE FROM sync_intervals WHERE accountId = :accountId")
    suspend fun deleteForAccount(accountId: Long)
}
