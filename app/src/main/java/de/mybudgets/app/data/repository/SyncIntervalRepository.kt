package de.mybudgets.app.data.repository

import de.mybudgets.app.data.db.SyncIntervalDao
import de.mybudgets.app.data.db.TransactionDao
import de.mybudgets.app.data.model.SyncInterval
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncIntervalRepository @Inject constructor(
    private val dao: SyncIntervalDao,
    private val transactionDao: TransactionDao
) {
    suspend fun getAllForAccount(accountId: Long): List<SyncInterval> =
        dao.getAllForAccount(accountId)

    suspend fun getHistoricalForAccount(accountId: Long): List<SyncInterval> =
        dao.getHistoricalForAccount(accountId)

    suspend fun insert(interval: SyncInterval): Long = dao.insert(interval)

    suspend fun deleteForAccount(accountId: Long) = dao.deleteForAccount(accountId)
    
    /**
     * Löscht die letzten N Sync-Intervalle und alle zugehörigen Transaktionen.
     * Nützlich für Debug/Testing nach Bulk-Sync.
     */
    suspend fun undoLastNSyncs(accountId: Long, count: Int): Int {
        val intervals = dao.getLastNSyncs(accountId, count)
        var deletedTxCount = 0
        
        intervals.forEach { interval ->
            deletedTxCount += transactionDao.deleteInDateRange(
                accountId, 
                interval.startDate, 
                interval.endDate
            )
        }
        
        dao.deleteLastNSyncs(accountId, count)
        return deletedTxCount
    }

    /**
     * Findet das nächste fromDate für historischen Sync.
     * 
     * WICHTIG: BBBank-Forward-Sync Prinzip:
     * - fromDate=null → Bank liefert ÄLTESTE 150 TX (z.B. 01.01.2020 - 15.03.2020)
     * - fromDate=16.03.2020 → Bank liefert 150 TX AB diesem Datum VORWÄRTS
     * 
     * Strategie: Erster Sync holt älteste Daten, dann immer VORWÄRTS bis Lücke geschlossen.
     * 
     * @return null = Lücke geschlossen oder keine Normal-Daten als Ziel
     *         0L = Kein historisches Intervall vorhanden → Start mit fromDate=null
     *         >0L = Nächstes fromDate für Forward-Sync
     */
    suspend fun getNextHistoricalSyncDate(accountId: Long): Long? {
        val TAG = "SyncIntervalRepo"
        
        val historicalIntervals = getHistoricalForAccount(accountId)
        de.mybudgets.app.util.AppLogger.i(TAG, "getNextHistoricalSyncDate($accountId):")
        de.mybudgets.app.util.AppLogger.i(TAG, "  historicalIntervals.size=${historicalIntervals.size}")
        historicalIntervals.forEachIndexed { i, interval ->
            val sdf = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.GERMANY)
            de.mybudgets.app.util.AppLogger.i(TAG, "    [$i] ${sdf.format(interval.startDate)} bis ${sdf.format(interval.endDate)}")
        }
        
        if (historicalIntervals.isEmpty()) {
            // Kein historisches Intervall → Erster Sync mit fromDate=null
            // Bank liefert automatisch die ÄLTESTEN 150 TX
            de.mybudgets.app.util.AppLogger.i(TAG, "  → RETURN 0L (Erster Sync: Bank liefert älteste Daten)")
            return 0L
        }
        
        // Finde neuestes historisches Intervall (wir lesen vorwärts!)
        val newestHistorical = historicalIntervals.maxByOrNull { it.endDate }!!
        val sdf = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.GERMANY)
        de.mybudgets.app.util.AppLogger.i(TAG, "  newestHistorical.endDate=${sdf.format(newestHistorical.endDate)}")
        
        // Prüfe ob es Normal-Sync Daten gibt (unser primäres Ziel)
        val allIntervals = getAllForAccount(accountId)
        val normalIntervals = allIntervals.filter { !it.isHistorical }
        de.mybudgets.app.util.AppLogger.i(TAG, "  normalIntervals.size=${normalIntervals.size}")
        
        // Berechne nächstes fromDate (1 Tag nach neuestem historischen)
        val gapStart = newestHistorical.endDate + 24 * 60 * 60 * 1000
        de.mybudgets.app.util.AppLogger.i(TAG, "  gapStart=${sdf.format(gapStart)}")
        
        if (normalIntervals.isNotEmpty()) {
            // Finde ältestes Normal-Intervall (unser Ziel)
            val oldestNormal = normalIntervals.minByOrNull { it.startDate }!!
            de.mybudgets.app.util.AppLogger.i(TAG, "  oldestNormal.startDate=${sdf.format(oldestNormal.startDate)}")
            
            if (gapStart < oldestNormal.startDate) {
                // Lücke noch nicht geschlossen → Weiter vorwärts laden
                de.mybudgets.app.util.AppLogger.i(TAG, "  → RETURN ${sdf.format(gapStart)} (Lücke noch offen, weiter vorwärts)")
                return gapStart
            }
            
            // Lücke geschlossen
            de.mybudgets.app.util.AppLogger.i(TAG, "  → RETURN null (Lücke geschlossen)")
            return null
        } else {
            // Kein Normal-Sync vorhanden → Lade bis heute
            val today = System.currentTimeMillis()
            val daysSinceNewest = (today - newestHistorical.endDate) / (24 * 60 * 60 * 1000)
            de.mybudgets.app.util.AppLogger.i(TAG, "  Kein Normal-Sync, daysSinceNewest=$daysSinceNewest Tage")
            
            if (daysSinceNewest > 1) {
                // Noch nicht bei heute → Weiter vorwärts laden
                de.mybudgets.app.util.AppLogger.i(TAG, "  → RETURN ${sdf.format(gapStart)} (Noch nicht bei heute)")
                return gapStart
            }
            
            // Bei heute angekommen
            de.mybudgets.app.util.AppLogger.i(TAG, "  → RETURN null (Bei heute angekommen)")
            return null
        }
    }

    /**
     * Schätzt wie viele TAN-Eingaben für vollständigen historischen Sync nötig sind.
     * BBBank: 1 TAN ≈ 1 Sync ≈ 150 TX
     * 
     * @return Anzahl geschätzter TAN-Anfragen (Gap-basiert, nicht zeitbasiert)
     */
    suspend fun estimateTanCount(accountId: Long): Int {
        val historicalIntervals = getHistoricalForAccount(accountId)
        val allIntervals = getAllForAccount(accountId)
        val normalIntervals = allIntervals.filter { !it.isHistorical }
        
        if (historicalIntervals.isEmpty()) {
            // Kein historischer Sync → mindestens 1 TAN für ersten Sync
            return 1
        }
        
        // Finde ältestes Normal + neuestes Historical
        val newestHistorical = historicalIntervals.maxByOrNull { it.endDate } ?: return 1
        val oldestNormal = normalIntervals.minByOrNull { it.startDate }
        
        val gapStart = newestHistorical.endDate + 24 * 60 * 60 * 1000
        val gapEnd = oldestNormal?.startDate ?: System.currentTimeMillis()
        
        if (gapStart >= gapEnd) return 0 // Keine Lücke
        
        // BBBank: ~150 TX pro Request, konservativ: 120 Tage pro Sync (bei 1-2 TX/Tag)
        val gapDays = (gapEnd - gapStart) / (24 * 60 * 60 * 1000)
        val estimatedSyncs = kotlin.math.ceil(gapDays / 120.0).toInt()
        
        return kotlin.math.max(1, estimatedSyncs)
    }
}
