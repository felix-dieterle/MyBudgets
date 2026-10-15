package de.mybudgets.app.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import de.mybudgets.app.data.api.UNIQUE_WORK_ONCE
import de.mybudgets.app.data.api.UNIQUE_WORK_PERIODIC
import de.mybudgets.app.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "BackendSyncScheduler"

/**
 * Plant den Backend-Sync.
 *
 * WICHTIG - der Name der Unique Work: einmaliger und periodischer Sync teilen sich bei
 * WorkManager einen Namensraum. Vor 05.10.2026 hiessen BEIDE "backend_sync". Der
 * periodische Work ist nie terminal (er steht dauerhaft auf ENQUEUED), deshalb hat
 * [ExistingWorkPolicy.KEEP] jeden manuellen Auftrag still verworfen: der Worker wurde
 * nie gestartet, es gab kein Log, das Symbol zeigte kein Laufverhalten, und die
 * Zaehlerstände kamen ausschliesslich aus dem 6-Stunden-Lauf.
 * Einmalig und periodisch haben deshalb jetzt getrennte Namen.
 */
object BackendSyncScheduler {

    /** Einmaliger Sync (App-Start, nach Buchungs- oder Bankaenderung, manuell). */
    const val UNIQUE_ONE_TIME = UNIQUE_WORK_ONCE

    /** Periodischer Sync, alle 6 Stunden - siehe [de.mybudgets.app.MyBudgetsApp]. */
    const val UNIQUE_PERIODIC = UNIQUE_WORK_PERIODIC

    /**
     * Altname aus Versionen vor 05.10.2026. Muss beim Start einmal geleert werden,
     * sonst blockiert der alte periodische Auftrag weiter alle einmaligen.
     */
    const val LEGACY_UNIQUE = "backend_sync"

    /** Normaler Sync: vorhandenen Auftrag nicht verdoppeln. */
    fun enqueue(context: Context) = enqueue(context, manual = false)

    /**
     * Ausdruecklich vom Nutzer angefordert. [ExistingWorkPolicy.REPLACE] ist hier zwingend:
     * bei KEEP wuerde ein bereits wartender Auftrag - auch einer im Retry-Backoff nach
     * einem Fehlschlag - den Wunsch des Nutzers ignorieren. REPLACE bricht ihn ab und
     * startet sofort neu.
     */
    fun enqueueManual(context: Context) = enqueue(context, manual = true)

    private fun enqueue(context: Context, manual: Boolean) {
        val prefs = context.getSharedPreferences("mybudgets_prefs", Context.MODE_PRIVATE)
        val syncEnabled = !prefs.getBoolean("offline_mode", true)
        val backendUrl = prefs.getString("backend_url", "") ?: ""
        if (!syncEnabled || backendUrl.isBlank()) {
            AppLogger.d(TAG, "enqueue: uebersprungen (offline_mode=$syncEnabled, url='$backendUrl')")
            return
        }

        val request = OneTimeWorkRequestBuilder<BackendSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()

        val policy = if (manual) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
        WorkManager.getInstance(context)
            .enqueueUniqueWork(UNIQUE_ONE_TIME, policy, request)

        // Vorher stand hier unabhaengig "eingeplant" - das war falsch: bei KEEP kann
        // WorkManager den Auftrag still verwerfen, und genau diese Zeile hat den
        // Fehler wochenlang als Erfolg tarnt. Der Auftrag ist jetzt wirklich neu,
        // deshalb ist die Meldung auch korrekt - der Lauf selbst loggt in doWork.
        if (manual) {
            AppLogger.i(TAG, "Manueller Sync angefordert (REPLACE: ein wartender Auftrag wird abgeloest)")
        } else {
            AppLogger.d(TAG, "Automatischer Sync angefordert (KEEP: ein laufender Auftrag bleibt)")
        }
        logStateAfterEnqueue(context)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Fragt den tatsaechlichen Zustand ab, statt ihn anzunehmen.
     *
     * ENQUEUED heisst: WorkManager hat den Auftrag, der Worker ist aber nicht gelaufen -
     * dann ist es die Netzwerkbedingung oder die Worker-Erzeugung, und genau das ist
     * bisher nie sichtbar geworden. RUNNING/SUCCEUED heisst: alles in Ordnung.
     */
    private fun logStateAfterEnqueue(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            delay(1500)
            runCatching {
                val infos = WorkManager.getInstance(appContext)
                    .getWorkInfosForUniqueWork(UNIQUE_ONE_TIME)
                    .get()
                AppLogger.i(
                    TAG,
                    "Work-Status: " + infos.joinToString(" | ") { "${it.state} tags=${it.tags}" }
                )
            }.onFailure {
                AppLogger.e(TAG, "Work-Status nicht abrufbar: ${it.message}", it)
            }
        }
    }
}
