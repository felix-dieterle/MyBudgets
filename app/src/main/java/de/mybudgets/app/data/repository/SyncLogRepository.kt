package de.mybudgets.app.data.repository

import de.mybudgets.app.data.db.SyncLogDao
import de.mybudgets.app.data.model.SyncLogEntry
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Zugriff auf das Sync-Protokoll (Room). Der Worker schreibt, die Protokoll-Ansicht liest.
 * Beim Schreiben wird auf [MAX_ENTRIES] gekuerzt, damit die Tabelle nicht unbegrenzt waechst.
 */
@Singleton
class SyncLogRepository @Inject constructor(
    private val dao: SyncLogDao
) {

    companion object {
        const val MAX_ENTRIES = 500

        const val DIRECTION_PULL = "PULL"
        const val DIRECTION_PUSH = "PUSH"
        const val DIRECTION_BOTH = "BOTH"
        const val DIRECTION_NONE = "NONE"
    }

    val entries: Flow<List<SyncLogEntry>> = dao.observeAll()

    suspend fun log(
        runId: Long,
        phase: String,
        direction: String,
        area: String,
        itemCount: Int,
        success: Boolean,
        message: String = ""
    ) {
        dao.insert(
            SyncLogEntry(
                runId = runId,
                phase = phase,
                direction = direction,
                area = area,
                itemCount = itemCount,
                success = success,
                message = message
            )
        )
        dao.prune(MAX_ENTRIES)
    }

    suspend fun recent(limit: Int): List<SyncLogEntry> = dao.recent(limit)

    suspend fun clear() = dao.clear()
}
