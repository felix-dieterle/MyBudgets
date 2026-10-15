package de.mybudgets.app.data.repository

import de.mybudgets.app.data.db.PatternEvaluationDao
import de.mybudgets.app.data.db.TransactionDao
import de.mybudgets.app.data.model.Transaction
import de.mybudgets.app.util.PatternKeyNormalizer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wendet im Dashboard gesetzte Kategorie-Overrides (US-004) auf App-Buchungen an - und
 * nimmt sie wieder zurueck. Das Matching laeuft ueber [PatternKeyNormalizer], also genau
 * den Schluessel, unter dem das Dashboard die Muster gruppiert.
 *
 * Angewendet wird nur auf Buchungen **ohne** Kategorie - eine vorhandene Einteilung wird
 * nie ueberschrieben. So bleibt die Aktion auch bei einem Fehlgriff folgenlos.
 */
@Singleton
class PatternOverrideRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val patternEvaluationDao: PatternEvaluationDao
) {

    data class Preview(val matched: Int, val uncategorized: Int) {
        val alreadyCategorized: Int get() = matched - uncategorized
    }

    data class ApplyResult(val matched: Int, val applied: Int) {
        val skipped: Int get() = matched - applied
    }

    /** Buchungen, deren normalisierte Beschreibung genau [key] ergibt. */
    suspend fun matchingTransactions(key: String): List<Transaction> =
        transactionDao.getAll().filter { PatternKeyNormalizer.normalize(it.description) == key }

    suspend fun preview(key: String): Preview {
        val matched = matchingTransactions(key)
        return Preview(matched.size, matched.count { it.categoryId == null })
    }

    suspend fun applyToMatching(key: String, categoryId: Long): ApplyResult {
        val matched = matchingTransactions(key)
        val ids = matched.filter { it.categoryId == null }.map { it.id }
        val applied = if (ids.isEmpty()) 0 else transactionDao.applyPatternOverride(ids, categoryId, key)
        return ApplyResult(matched.size, applied)
    }

    /** Anzahl der Buchungen, die aktuell auf [key] zurueckgehen (fuer die Ruecknahme). */
    suspend fun countApplied(key: String): Int = transactionDao.countByPatternOverrideKey(key)

    suspend fun reset(key: String): Int = transactionDao.resetPatternOverride(key)

    /**
     * Server-Overrides, deren Schluessel im aktuellen Server-Abzug fehlt - sie wurden im
     * Dashboard geloescht/geleert. Nur Zeilen mit Herkunft 'server' zaehlen, damit lokal
     * angelegte Bewertungen nie als "zurueckgenommen" gelten.
     */
    suspend fun removedServerOverrideKeys(serverKeys: Set<String>, version: String): List<String> =
        patternEvaluationDao.getServerCategoryOverrides(version)
            .map { it.patternKey }
            .filter { it !in serverKeys }
            .distinct()
}
