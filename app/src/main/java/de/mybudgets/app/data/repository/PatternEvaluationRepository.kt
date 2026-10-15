package de.mybudgets.app.data.repository

import de.mybudgets.app.data.db.PatternEvaluationDao
import de.mybudgets.app.data.model.PatternEvaluation
import de.mybudgets.app.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PatternEvaluationRepository @Inject constructor(
    private val dao: PatternEvaluationDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun observeAll(version: String): Flow<List<PatternEvaluation>> = dao.observeAll(version)
    suspend fun getAll(version: String): List<PatternEvaluation> = dao.getAll(version)
    suspend fun get(key: String, version: String): PatternEvaluation? = dao.get(key, version)
    suspend fun save(evaluation: PatternEvaluation): Long = dao.upsert(evaluation)
    suspend fun delete(key: String, version: String) = dao.delete(key, version)

    fun saveAsync(evaluation: PatternEvaluation) {
        scope.launch {
            try {
                dao.upsert(evaluation)
            } catch (e: Exception) {
                AppLogger.e(TAG, "saveAsync failed für ${evaluation.patternKey}", e)
            }
        }
    }

    /**
     * Uebernimmt Server-Bewertungen aus dem Dashboard (US-004, Back-Sync).
     *
     * Konflikt-Regel: Last-Writer-Wins ueber `updatedAt` - eine Server-Zeile wird nur
     * uebernommen, wenn sie neuer ist als die lokale (oder lokal noch nicht existiert).
     * Zeilen mit fremder `matcherVersion` werden ignoriert (Sicherung wie im Dashboard).
     *
     * @return Anzahl tatsaechlich uebernommener/aktualisierter Zeilen.
     */
    suspend fun merge(remote: List<PatternEvaluation>, version: String): Int {
        if (remote.isEmpty()) return 0
        val local = dao.getAll(version).associateBy { it.patternKey }
        var applied = 0
        for (r in remote) {
            if (r.matcherVersion != version) continue
            val existing = local[r.patternKey]
            if (existing == null || r.updatedAt > existing.updatedAt) {
                dao.upsert(r.copy(id = existing?.id ?: 0))
                applied++
            }
        }
        return applied
    }

    companion object {
        private const val TAG = "PatternEvaluationRepo"
    }
}
