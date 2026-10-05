package de.mybudgets.app.worker

import android.content.Context
import android.content.SharedPreferences
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.mybudgets.app.data.api.ApiClient
import de.mybudgets.app.data.api.PREF_ORPHAN_COUNT
import de.mybudgets.app.data.api.PREF_REMOTE_ACC_COUNT
import de.mybudgets.app.data.api.PREF_REMOTE_TX_COUNT
import de.mybudgets.app.data.api.PREF_REPUSH_ORPHANS
import de.mybudgets.app.data.api.SYNC_STATE_ERROR
import de.mybudgets.app.data.api.SYNC_STATE_OK
import de.mybudgets.app.data.api.SYNC_STATE_RUNNING
import de.mybudgets.app.data.api.dto.TransactionDto
import de.mybudgets.app.data.model.Transaction
import de.mybudgets.app.data.model.TransactionType
import de.mybudgets.app.data.repository.AccountRepository
import de.mybudgets.app.data.repository.TransactionRepository
import de.mybudgets.app.util.AppLogger

private const val TAG = "BackendSyncWorker"

/** Seitenlaenge beim Abruf der Backend-Buchungen - auch die untere Grenze fuer "letzte Seite voll". */
private const val PAGE_SIZE = 200

/** Harte Obergrenze fuer das Zaehlen der Remote-Buchungen, damit ein Fehler nicht ewig laeuft. */
private const val MAX_PAGES = 20

/** Wie viele Buchungen fuer Push und ID-Abgleich geprueft werden. */
private const val PUSH_WINDOW = 500

@HiltWorker
class BackendSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val accountRepo: AccountRepository,
    private val transactionRepo: TransactionRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("mybudgets_prefs", Context.MODE_PRIVATE)
        val backendUrl = (prefs.getString("backend_url", "") ?: "").trim()
        val apiKey     = (prefs.getString("api_key", "") ?: "").trim()
        val syncEnabled = !prefs.getBoolean("offline_mode", true)
        val keyInfo = if (apiKey.isEmpty()) "nicht gesetzt" else "gesetzt (${apiKey.length} Zeichen)"

        if (!syncEnabled || backendUrl.isBlank()) {
            AppLogger.d(TAG, "doWork übersprungen: syncEnabled=$syncEnabled, url='$backendUrl'")
            return Result.success()
        }

        return try {
            AppLogger.i(TAG, "doWork: Starte Backend-Sync gegen $backendUrl (apiKey: $keyInfo)")
            prefs.edit().putString("sync_state", SYNC_STATE_RUNNING)
                .remove("sync_last_error").apply()
            val api = ApiClient.getService(backendUrl, apiKey)
            var httpFailures = 0

            // Als eigene Funktion, weil der Repush-Zweig sie ein zweites Mal braucht.
            suspend fun pushMissing(): Int {
                val allLocal = transactionRepo.getRecent(PUSH_WINDOW)
                val unpushed = allLocal.filter { it.remoteId == null }
                AppLogger.d(TAG, "pushMissing: ${unpushed.size} lokale Buchungen ohne remoteId")
                var pushed = 0
                unpushed.forEach { tx ->
                    val resp = api.createTransaction(tx.toDto())
                    if (resp.isSuccessful) {
                        val remoteId = resp.body()?.get("id")?.toString()
                        if (remoteId != null) {
                            transactionRepo.save(tx.copy(remoteId = remoteId))
                            pushed++
                        } else {
                            httpFailures++
                            AppLogger.w(TAG, "pushMissing: HTTP ${resp.code()} fuer txId=${tx.id}, aber ohne id im Body")
                        }
                    } else {
                        httpFailures++
                        AppLogger.w(TAG, "pushMissing: Upload fehlgeschlagen fuer txId=${tx.id}: HTTP ${resp.code()}")
                    }
                }
                return pushed
            }

            val pushed = pushMissing()

// Pull remote transactions - deduplicate against known remoteIds.
            // Die Seiten werden durchzaehlt und dabei sofort importiert, damit der Dialog
            // eine ECHTE Backend-Zahl zeigen kann und nicht nur die Groesse des ersten Abrufs.
            val existingRemoteIds = transactionRepo.getRecent(PUSH_WINDOW).mapNotNull { it.remoteId }.toHashSet()
            val backendIds = HashSet<String>()
            var pullComplete = true
            var remoteTxCount = 0
            var pulled = 0
            var offset = 0
            var reachedLastPage = false
            while (offset < MAX_PAGES * PAGE_SIZE) {
                val page = api.getTransactions(limit = PAGE_SIZE, offset = offset)
                if (!page.isSuccessful) {
                    httpFailures++
                    pullComplete = false
                    AppLogger.w(TAG, "doWork: Server-Abruf fehlgeschlagen (offset=$offset): HTTP ${page.code()}")
                    break
                }
                val dtos = page.body().orEmpty()
                remoteTxCount += dtos.size
                dtos.forEach { dto -> backendIds.add(dto.id.toString()) }
                dtos.filter { dto -> dto.id.toString() !in existingRemoteIds }
                    .forEach { dto ->
                        existingRemoteIds.add(dto.id.toString())
                        transactionRepo.save(dto.toTransaction())
                        pulled++
                    }
                if (dtos.size < PAGE_SIZE) {
                    reachedLastPage = true
                    break
                }
                offset += PAGE_SIZE
            }
            // Genau MAX_PAGES * PAGE_SIZE Buchungen: die letzte Seite war voll, es kann mehr geben.
            if (!reachedLastPage && pullComplete) {
                pullComplete = false
                AppLogger.w(TAG, "doWork: Abruf nach $MAX_PAGES Seiten gestoppt - Zaehler unvollstaendig")
            }
            if (pulled > 0) AppLogger.i(TAG, "doWork: $pulled neue Buchungen vom Server importiert")

            // Verwaiste remoteIds: lokal verweist eine Buchung auf eine Backend-ID, die es dort
            // nicht (mehr) gibt. Ein normaler Sync ueberspringt sie (remoteId != null), die
            // Zaehler bleiben also dauerhaft auseinander - ohne diesen Schritt gibt es keinen Weg,
            // den Zustand aufzuloesen. null = nicht bestimmbar, weil der Abruf unvollstaendig war.
            val orphans: Set<String>? = if (pullComplete) {
                transactionRepo.getAllRemoteIds() - backendIds
            } else {
                null
            }
            AppLogger.i(
                TAG,
                if (orphans == null) "doWork: Abruf unvollstaendig - verwaiste IDs nicht bestimmbar"
                else "doWork: ${orphans.size} verwaiste remoteId(s) - lokal vorhanden, im Backend unbekannt"
            )

            // Auf Nutzerwunsch: genau diese Buchungen zuruecksetzen und erneut senden.
            if (prefs.getBoolean(PREF_REPUSH_ORPHANS, false)) {
                prefs.edit().putBoolean(PREF_REPUSH_ORPHANS, false).apply()
                if (!orphans.isNullOrEmpty()) {
                    val cleared = transactionRepo.clearRemoteIds(orphans.toList())
                    AppLogger.w(TAG, "doWork: ${cleared} verwaiste remoteId(s) zurueckgesetzt (Nutzerwunsch)")
                    val repushed = pushMissing()
                    AppLogger.i(TAG, "doWork: $repushed Buchungen nach zurueckgesetzten IDs erneut gesendet")
                } else {
                    AppLogger.i(TAG, "doWork: Repush angefordert, aber keine verwaisten IDs gefunden")
                }
            }

            // Konten nur zaehlen - es gibt noch kein Mapping lokal<->Backend (kein Feld dafuer).
            val accResp = api.getAccounts()
            var remoteAccCount = -1
            if (accResp.isSuccessful) {
                remoteAccCount = accResp.body()?.size ?: 0
            } else {
                httpFailures++
                AppLogger.w(TAG, "doWork: Konten-Abruf fehlgeschlagen: HTTP ${accResp.code()}")
            }

            if (httpFailures > 0) {
                // Kein "OK": der Lauf war nicht fehlerfrei, der Backend-Stand gilt als unbekannt.
                markFailed(
                    prefs,
                    "$httpFailures Abfrage(n) mit HTTP-Fehler beantwortet (davon $pushed erfolgreich hochgeladen)",
                    null
                )
                return Result.retry()
            }

            prefs.edit()
                .putLong("last_sync_time", System.currentTimeMillis())
                .putString("sync_state", SYNC_STATE_OK)
                .putInt(PREF_REMOTE_TX_COUNT, remoteTxCount)
                .putInt(PREF_REMOTE_ACC_COUNT, remoteAccCount)
                .putInt(PREF_ORPHAN_COUNT, orphans?.size ?: -1)
                .remove("sync_last_error")
                .apply()
            AppLogger.i(TAG, "doWork: Backend-Sync abgeschlossen, remote=$remoteTxCount Buchungen / $remoteAccCount Konten, hochgeladen=$pushed")
            Result.success()
        } catch (e: java.net.SocketTimeoutException) {
            markFailed(prefs, "Zeitüberschreitung bei $backendUrl - Server läuft nicht oder blockiert die Anfrage", e)
            Result.retry()
        } catch (e: java.net.UnknownHostException) {
            markFailed(prefs, "Host aus $backendUrl nicht auflösbar - falsche URL oder falsches WLAN", e)
            Result.retry()
        } catch (e: java.net.ConnectException) {
            markFailed(prefs, "Verbindung zu $backendUrl abgelehnt - läuft der PHP-Server?", e)
            Result.retry()
        } catch (e: java.io.IOException) {
            markFailed(prefs, "Netzwerkfehler bei $backendUrl: ${e.javaClass.simpleName}: ${e.message}", e)
            Result.retry()
        } catch (e: Exception) {
            markFailed(prefs, "Backend-Sync fehlgeschlagen: ${e.javaClass.simpleName}: ${e.message}", e)
            Result.retry()
        }
    }

    /** Fehler merken, damit das Sync-Icon nach einem gescheiterten Lauf rot bleibt. */
    private fun markFailed(prefs: SharedPreferences, reason: String, e: Throwable?) {
        if (e != null) {
            AppLogger.e(TAG, "doWork: $reason (${e.javaClass.simpleName}: ${e.message})", e)
        } else {
            AppLogger.e(TAG, "doWork: $reason")
        }
        prefs.edit()
            .putString("sync_state", SYNC_STATE_ERROR)
            .putString("sync_last_error", reason)
            .apply()
    }

    private fun Transaction.toDto() = TransactionDto(
        id                   = id,
        accountId            = accountId,
        virtualAccountId     = virtualAccountId,
        amount               = amount,
        description          = description,
        date                 = date,
        type                 = type.name,
        categoryId           = categoryId,
        note                 = note,
        isRecurring          = isRecurring,
        recurringIntervalDays = recurringIntervalDays,
        remoteId             = remoteId,
        createdAt            = createdAt
    )

    private fun TransactionDto.toTransaction() = Transaction(
        accountId            = accountId,
        virtualAccountId     = virtualAccountId,
        amount               = amount,
        description          = description,
        date                 = date,
        type                 = runCatching { TransactionType.valueOf(type) }.getOrDefault(TransactionType.EXPENSE),
        categoryId           = categoryId,
        note                 = note,
        isRecurring          = isRecurring,
        recurringIntervalDays = recurringIntervalDays,
        remoteId             = id.toString(),
        createdAt            = createdAt
    )
}
