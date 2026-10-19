package de.mybudgets.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import de.mybudgets.app.data.model.SyncLogEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncLogDao {

    @Insert
    suspend fun insert(entry: SyncLogEntry): Long

    @Query("SELECT * FROM sync_log ORDER BY timestamp DESC, id DESC")
    fun observeAll(): Flow<List<SyncLogEntry>>

    @Query("SELECT * FROM sync_log ORDER BY timestamp DESC, id DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<SyncLogEntry>

    /** Behaelt nur die neuesten [keep] Zeilen - das Protokoll darf nicht unbegrenzt wachsen. */
    @Query("DELETE FROM sync_log WHERE id NOT IN (SELECT id FROM sync_log ORDER BY id DESC LIMIT :keep)")
    suspend fun prune(keep: Int)

    @Query("DELETE FROM sync_log")
    suspend fun clear()
}
