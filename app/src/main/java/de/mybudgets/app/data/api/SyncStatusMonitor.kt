package de.mybudgets.app.data.api

import android.content.Context
import android.content.SharedPreferences
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import de.mybudgets.app.data.repository.AccountRepository
import de.mybudgets.app.data.repository.TransactionRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Die Zustaende des Sync-Indikators neben dem Health-Laempchen. Genau ein
 * Zustand ist gleichzeitig sichtbar - [SyncStatusMonitor.resolve] entscheidet nach Prioritaet.
 */
enum class SyncIndicatorState {
    /** Offline-Modus aktiv oder keine Backend-URL: es wird bewusst nicht abgeglichen. */
    DISABLED,

    /** Ein [de.mybudgets.app.worker.BackendSyncWorker] laeuft gerade. */
    SYNCING,

    /** Der letzte Abgleich ist mit Fehler beendet worden. */
    ERROR,

    /** Lokale Aenderungen warten noch auf den Upload. */
    PENDING,

    /**
     * Noch nie ein vollstaendiger, fehlerfreier Abgleich mit dem Backend. Wichtig fuer die
     * Ehrlichkeit der Anzeige: "0 ausstehende Aenderungen" heisst nur, dass es nichts zu
     * pushen gab - nicht, dass das Backend die lokalen Daten kennt.
     */
    NEVER_SYNCED,

    /**
     * Ein fehlerfreier Abgleich fand statt, trotzdem haben Backend und Geraet unterschiedliche
     * Anzahlen. Daten fehlen auf einer der beiden Seiten - also asynchron. Ein gruenes Icon
     * waere hier eine Falschaussage: es behauptet einen Stand, den es nicht gibt.
     */
    MISMATCH,

    /** Lokaler und Backend-Stand sind nach einem fehlerfreien Abgleich identisch. */
    UP_TO_DATE
}

/**
 * Richtung, in die ein laufender Abgleich gerade arbeitet. Der Doppelpfeil-Indikator hebt
 * diese Richtung hervor - ohne sie waere "Synchronisiert gerade" nur eine Behauptung.
 */
enum class SyncDirection {
    /** Daten kommen vom Backend auf das Geraet. */
    PULL,

    /** Lokale Aenderungen gehen zum Backend. */
    PUSH,

    /** Beides im selben Schritt (Konten/Kategorien abgleichen). */
    BOTH
}

/**
 * Vollstaendige Beschreibung des Sync-Zustands. [SyncStatusMonitor.status] steuert Icon und
 * Farbe, [report] fuellt den Detail-Dialog nach einem Klick auf das Icon.
 *
 * [remoteTransactionCount] / [remoteAccountCount] sind -1, solange der Backend-Stand nicht
 * verifiziert wurde. Dann darf die Anzeige keinen Vergleich erfinden.
 */
data class SyncStatusInfo(
    val state: SyncIndicatorState,
    val pendingCount: Int,
    val localTransactionCount: Int,
    val localAccountCount: Int,
    val remoteTransactionCount: Int,
    val remoteAccountCount: Int,
    val lastSyncTime: Long,
    val lastError: String,
    val backendUrl: String,
    val queued: Boolean,
    val offlineMode: Boolean,
    val orphanCount: Int,
    /** Aktuelle Phase des laufenden Syncs aus [PREF_SYNC_PHASE] - leer, wenn kein Sync laeuft. */
    val phase: String = ""
) {
    /** true, wenn der Backend-Stand durch einen fehlerfreien Abgleich belegt ist. */
    val remoteKnown: Boolean get() = remoteTransactionCount >= 0 && remoteAccountCount >= 0

    /**
     * true, wenn ein belegter Remote-Stand vom lokalen abweicht. Der Vergleich gehoert hierher,
     * damit Icon, Kurztext und Detailbericht dieselbe Bedingung benutzen.
     */
    val mismatched: Boolean
        get() = remoteKnown &&
            (localTransactionCount != remoteTransactionCount || localAccountCount != remoteAccountCount)

    /**
     * Richtung des laufenden Syncs, abgeleitet aus dem Praefix der Phase: "PUSH_*" laedt hoch,
     * "PULL_*" zieht vom Backend, "SYNC_*" macht beides (Konten/Kategorien abgleichen).
     * null = kein Sync laeuft oder die Phase ist unbekannt.
     */
    val direction: SyncDirection?
        get() = when {
            phase.startsWith("PUSH") -> SyncDirection.PUSH
            phase.startsWith("PULL") -> SyncDirection.PULL
            phase.startsWith("SYNC") -> SyncDirection.BOTH
            else -> null
        }
}

private const val PREFS = "mybudgets_prefs"
private const val PREF_OFFLINE = "offline_mode"
private const val PREF_URL = "backend_url"
private const val PREF_LAST_SYNC = "last_sync_time"
private const val PREF_SYNC_STATE = "sync_state"
private const val PREF_SYNC_ERROR = "sync_last_error"

/** Vom Worker gesetzt: Anzahl Buchungen/Konten, die der Backend beim letzten Lauf wirklich hatte. */
internal const val PREF_REMOTE_TX_COUNT = "remote_tx_count"
internal const val PREF_REMOTE_ACC_COUNT = "remote_acc_count"

/** Vom Worker gesetzt: Anzahl Buchungen mit einer remoteId, die das Backend nicht kennt. -1 = unbestimmbar. */
internal const val PREF_ORPHAN_COUNT = "orphan_count"

/** Vom UI gesetzt: verwaiste remoteIds zuruecksetzen und erneut senden. */
const val PREF_REPUSH_ORPHANS = "repush_orphans"

/**
 * Namen der Unique Work. Einmalig und periodisch teilen sich bei WorkManager einen
 * Namensraum - vor 05.10.2026 hiessen beide "backend_sync", wodurch der nie terminale
 * Perioden-Work jeden manuellen Auftrag via KEEP still verworfen hat.
 * Hier definiert, weil der Monitor sie beobachtet und der Worker sie anlegt.
 */
const val UNIQUE_WORK_ONCE = "backend_sync_now"
const val UNIQUE_WORK_PERIODIC = "backend_sync_periodic"

internal const val SYNC_STATE_RUNNING = "RUNNING"
internal const val SYNC_STATE_OK = "OK"
internal const val SYNC_STATE_ERROR = "ERROR"

/**
 * Vom Worker gesetzte aktuelle Phase des Laufs ("PUSH_TX", "PULL_TX", "SYNC_ACC", ...).
 * Der Indikator hebt daraus die aktive Richtung hervor. Wird am Ende des Laufs entfernt -
 * eine stehengebliebene Phase wuerde einen Pfeil dauerhaft hervorheben.
 */
internal const val PREF_SYNC_PHASE = "sync_phase"

internal const val PHASE_SYNC_ACCOUNTS = "SYNC_ACC"
internal const val PHASE_SYNC_CATEGORIES = "SYNC_CATEGORIES"
internal const val PHASE_PUSH_TX = "PUSH_TX"
internal const val PHASE_PULL_TX = "PULL_TX"
internal const val PHASE_PUSH_REPUSH = "PUSH_REPUSH"
internal const val PHASE_PULL_PATTERNS = "PULL_PATTERNS"

/** Kein Richtungsbezug: der Eintrag beschreibt den Lauf als Ganzes (Abschluss oder Fehler). */
internal const val PHASE_RUN = "RUN"

/**
 * Liefert dem Status-Icon seinen Zustand aus drei unabhaengigen Quellen, damit kein
 * Polling noetig ist:
 *
 * 1. [SharedPreferences] - Offline-Modus, URL, letzter Sync, letzter Fehler, verifizierter
 *    Remote-Stand (vom Worker nach einem fehlerfreien Lauf geschrieben)
 * 2. WorkManager - laeuft der Unique-Work `"backend_sync"` gerade?
 * 3. Room - wie viele Buchungen haben noch keine `remoteId`?
 * 4. Room - Gesamtzahl lokaler Buchungen und Konten (fuer den Vergleich Remote vs. Lokal)
 *
 * Alle Flows emittieren nur bei tatsaechlicher Aenderung, das Icon bekommt also
 * Updates ohne Polling-Batterielast. Fehlt eine Quelle (Sync nie gelaufen, kein Work
 * eingeplant), wird sie neutral behandelt statt die Anzeige zu verfaelschen.
 */
@Singleton
class SyncStatusMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transactionRepo: TransactionRepository,
    private val accountRepo: AccountRepository
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun prefs(): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val prefChanges: Flow<Unit> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(Unit)
        }
        val p = prefs()
        p.registerOnSharedPreferenceChangeListener(listener)
        trySend(Unit)
        awaitClose { p.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    /**
     * true, solange ein Sync laeuft - WorkManager oder der Worker selbst meldet es.
     * Beide Unique Works werden beobachtet: ein Lauf kann sowohl vom einmaligen als auch
     * vom 6-Stunden-Auftrag kommen, und die Anzeige darf keinen davon uebersehen.
     */
    private val workRunning: Flow<Boolean> = combine(
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(UNIQUE_WORK_ONCE),
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(UNIQUE_WORK_PERIODIC)
    ) { once, periodic ->
        (once + periodic).any { it.state == androidx.work.WorkInfo.State.RUNNING }
    }

    val status: StateFlow<SyncStatusInfo> = combine(
        prefChanges,
        workRunning,
        transactionRepo.observeUnsyncedCount(),
        transactionRepo.observeCount(),
        accountRepo.observeCount()
    ) { _, running, pending, localTx, localAcc -> resolve(running, pending, localTx, localAcc) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000L), resolve(false, 0, 0, 0))

    private fun resolve(running: Boolean, pending: Int, localTx: Int, localAcc: Int): SyncStatusInfo {
        val p = prefs()
        val offlineMode = p.getBoolean(PREF_OFFLINE, true)
        val url = (p.getString(PREF_URL, "") ?: "").trim()
        val workerState = (p.getString(PREF_SYNC_STATE, "") ?: "").trim()
        val lastError = (p.getString(PREF_SYNC_ERROR, "") ?: "").trim()

        // Nur ein vom Worker nach einem fehlerfreien Lauf gesetzter Wert gilt als Verifikation.
        // -1 = nie verifiziert, also darf die Anzeige keinen Remote-Stand behaupten.
        val remoteTx = p.getInt(PREF_REMOTE_TX_COUNT, -1)
        val remoteAcc = p.getInt(PREF_REMOTE_ACC_COUNT, -1)
        val verified = remoteTx >= 0 && remoteAcc >= 0

        // "verified" hiess bisher nur: der Vergleich fand statt. Dass beide Seiten gleich viele
        // Daten haben, war nie Bedingung - dadurch blieb das Icon gruen, obwohl das Backend
        // Buchungen nicht kannte (gemeldet 05.10.2026).
        val mismatched = verified && (localTx != remoteTx || localAcc != remoteAcc)

        val state = when {
            offlineMode || url.isEmpty() -> SyncIndicatorState.DISABLED
            running || workerState == SYNC_STATE_RUNNING -> SyncIndicatorState.SYNCING
            workerState == SYNC_STATE_ERROR -> SyncIndicatorState.ERROR
            pending > 0 -> SyncIndicatorState.PENDING
            !verified -> SyncIndicatorState.NEVER_SYNCED
            mismatched -> SyncIndicatorState.MISMATCH
            else -> SyncIndicatorState.UP_TO_DATE
        }

        return SyncStatusInfo(
            state = state,
            pendingCount = pending,
            localTransactionCount = localTx,
            localAccountCount = localAcc,
            remoteTransactionCount = remoteTx,
            remoteAccountCount = remoteAcc,
            lastSyncTime = p.getLong(PREF_LAST_SYNC, 0L),
            lastError = lastError,
            backendUrl = url,
            queued = running || workerState == SYNC_STATE_RUNNING,
            offlineMode = offlineMode,
            orphanCount = if (verified) p.getInt(PREF_ORPHAN_COUNT, -1) else -1,
            phase = (p.getString(PREF_SYNC_PHASE, "") ?: "").trim()
        )
    }
}