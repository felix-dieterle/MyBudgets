package de.mybudgets.app.worker

import android.content.Context
import android.content.SharedPreferences
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.mybudgets.app.data.api.ApiClient
import de.mybudgets.app.data.api.ApiService
import de.mybudgets.app.data.api.PREF_ORPHAN_COUNT
import de.mybudgets.app.data.api.PREF_REMOTE_ACC_COUNT
import de.mybudgets.app.data.api.PREF_REMOTE_TX_COUNT
import de.mybudgets.app.data.api.PREF_REPUSH_ORPHANS
import de.mybudgets.app.data.api.PREF_SYNC_PHASE
import de.mybudgets.app.data.api.PHASE_PULL_PATTERNS
import de.mybudgets.app.data.api.PHASE_PULL_TX
import de.mybudgets.app.data.api.PHASE_PUSH_REPUSH
import de.mybudgets.app.data.api.PHASE_PUSH_TX
import de.mybudgets.app.data.api.PHASE_RUN
import de.mybudgets.app.data.api.PHASE_SYNC_ACCOUNTS
import de.mybudgets.app.data.api.PHASE_SYNC_CATEGORIES
import de.mybudgets.app.data.api.SYNC_STATE_ERROR
import de.mybudgets.app.data.api.SYNC_STATE_OK
import de.mybudgets.app.data.api.SYNC_STATE_RUNNING
import de.mybudgets.app.data.api.dto.AccountDto
import de.mybudgets.app.data.api.dto.CategoryDto
import de.mybudgets.app.data.api.dto.TransactionDto
import de.mybudgets.app.data.model.Category
import de.mybudgets.app.data.model.PatternEvaluation
import de.mybudgets.app.data.model.Transaction
import de.mybudgets.app.data.model.TransactionType
import de.mybudgets.app.data.repository.AccountRepository
import de.mybudgets.app.data.repository.CategoryRepository
import de.mybudgets.app.data.repository.PatternEvaluationRepository
import de.mybudgets.app.data.repository.PatternOverrideRepository
import de.mybudgets.app.data.repository.SyncLogRepository
import de.mybudgets.app.data.repository.TransactionRepository
import de.mybudgets.app.util.AppLogger
import de.mybudgets.app.util.PatternEvaluationSuggestion

private const val TAG = "BackendSyncWorker"

/** Seitenlaenge beim Abruf der Backend-Buchungen - auch die untere Grenze fuer "letzte Seite voll". */
private const val PAGE_SIZE = 200

/** Harte Obergrenze fuer das Zaehlen der Remote-Buchungen, damit ein Fehler nicht ewig laeuft. */
private const val MAX_PAGES = 20

/** Alle Buchungen ohne remoteId werden hochgeladen - bewusst ohne Fenster, s. pushMissing(). */

/** Fortschrittsmeldung alle n Uploads, damit lange Laueufe sichtbar bleiben. */
private const val PUSH_PROGRESS_EVERY = 100
const val PREF_UNKNOWN_REMOTE_CATEGORIES = "unknown_remote_categories"

/** Vorgemerkte Ruecknahmen angewendeter Overrides (US-004) - wartet auf Nutzerbestaetigung. */
const val PREF_PENDING_OVERRIDE_RESETS = "pending_override_resets"

@HiltWorker
class BackendSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val accountRepo: AccountRepository,
    private val categoryRepo: CategoryRepository,
    private val transactionRepo: TransactionRepository,
    private val patternEvalRepo: PatternEvaluationRepository,
    private val patternOverrideRepo: PatternOverrideRepository,
    private val syncLogRepo: SyncLogRepository
) : CoroutineWorker(appContext, params) {

    companion object {
        private val runningSync = java.util.concurrent.atomic.AtomicBoolean(false)
    }

    /**
     * Meldet die aktuell laufende Phase nach aussen, damit der Doppelpfeil-Indikator die aktive
     * Richtung hervorheben kann. null entfernt sie wieder - eine stehengebliebene Phase wuerde
     * einen Pfeil dauerhaft hervorheben.
     */
    private fun setPhase(prefs: SharedPreferences, phase: String?) {
        val editor = prefs.edit()
        if (phase == null) editor.remove(PREF_SYNC_PHASE) else editor.putString(PREF_SYNC_PHASE, phase)
        editor.apply()
    }

    /**
     * Schreibt eine Zeile ins Sync-Protokoll. Fehler dabei duerfen den Sync nie stoppen -
     * das Protokoll ist Beiwerk, der Abgleich ist der Zweck.
     */
    private suspend fun logPhase(
        runId: Long,
        phase: String,
        direction: String,
        area: String,
        itemCount: Int,
        success: Boolean,
        message: String = ""
    ) {
        runCatching {
            syncLogRepo.log(runId, phase, direction, area, itemCount, success, message)
        }.onFailure {
            AppLogger.w(TAG, "Protokoll-Eintrag '$phase' nicht geschrieben: ${it.message}")
        }
    }

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

        // Zwei syncs gleichzeitig? WorkManager serialisiert nur innerhalb eines Unique-Work-Namens,
        // und es laufen zwei: "backend_sync_now" (manuell, REPLACE) und "backend_sync_periodic"
        // (alle 6 h, KEEP). Ohne diese Sperre haben sich ein manueller und ein periodischer Lauf
        // ueberschnitten - sichtbar daran, dass das Sync-Icon mittendrinnen ihren Zustand verlor.
        if (!runningSync.compareAndSet(false, true)) {
            AppLogger.w(TAG, "doWork: uebersprungen - es laeuft bereits ein Sync")
            return Result.success()
        }

        val runId = System.currentTimeMillis()

        /** Ein Eintrag fuer den Lauf als Ganzes - auf dem Fehlerpfad ist er der einzige. */
        suspend fun logRunFailed(reason: String) =
            logPhase(runId, PHASE_RUN, SyncLogRepository.DIRECTION_NONE, "Lauf", 0, false, reason)

        return try {
            AppLogger.i(TAG, "doWork: Starte Backend-Sync gegen $backendUrl (apiKey: $keyInfo)")
            prefs.edit().putString("sync_state", SYNC_STATE_RUNNING)
                .remove("sync_last_error").apply()
            val api = ApiClient.getService(backendUrl, apiKey)
            var httpFailures = 0
            // 401/403 ist ein dauerhafter Fehler - ein falscher API-Key wird durch Wiederholung
            // nicht besser. Vorher retried der Worker alles, damit lag er bei falschem Key in einem
            // Backoff bis zu 5 Stunden, in dem der manuelle Sync verworfen wurde.
            var authFailed = false

            // Konten zuerst: transactions.account_id hat einen Fremdschluessel auf accounts.id.
            // Ohne diesen Schritt schlug am 05.10.2026 jeder Upload mit einem FK-Verstoss fehl
            // (SQLSTATE 23000 / 1452), weil das Backend-accounts leer war.
            setPhase(prefs, PHASE_SYNC_ACCOUNTS)
            val accountMap = syncAccounts(api, prefs)
            logPhase(
                runId, PHASE_SYNC_ACCOUNTS, SyncLogRepository.DIRECTION_BOTH, "Konten",
                accountMap.size, true, "${accountMap.size} zugeordnet"
            )

            // Kategorien ebenfalls vorab abgleichen: transactions.category_id verweist auf
            // categories.id, und das Backend kennt nur die 22 vorinstallierten Ids 1..22,
            // waehrend lokal Ids bis 50 vergeben sind.
            setPhase(prefs, PHASE_SYNC_CATEGORIES)
            val categoryMap = syncCategories(api, prefs)
            logPhase(
                runId, PHASE_SYNC_CATEGORIES, SyncLogRepository.DIRECTION_BOTH, "Kategorien",
                categoryMap.size, true, "${categoryMap.size} zugeordnet"
            )

            // Als eigene Funktion, weil der Repush-Zweig sie ein zweites Mal braucht.
            suspend fun pushMissing(): Int {
                // Kein Limit: bei 2322 verwaisten Buchungen waere ein Fenster von 500
                // stillschweigend auf 500 gekuerzt worden - der Sync haette "fertig"
                // gemeldet und 1800 Buchungen nie hochgeladen.
                val unpushed = transactionRepo.getUnsynced()
                AppLogger.d(TAG, "pushMissing: ${unpushed.size} lokale Buchungen ohne remoteId")
                var pushed = 0
                unpushed.forEachIndexed { index, tx ->
                    // Ohne zuordenbare Konto-ID waere der Upload ein sicherer FK-Fehler.
                    val backendAccountId = accountMap[tx.accountId]
                    if (backendAccountId == null) {
                        httpFailures++
                        AppLogger.w(
                            TAG,
                            "pushMissing: txId=${tx.id} uebersprungen - Konto ${tx.accountId} " +
                                "existiert im Backend nicht"
                        )
                        return@forEachIndexed
                    }
                    val backendCategoryId = tx.categoryId?.let { categoryMap[it] }
                    val resp = api.createTransaction(
                        tx.toDto().copy(
                            accountId = backendAccountId,
                            categoryId = backendCategoryId
                        )
                    )
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
                        if (resp.code() == 401 || resp.code() == 403) authFailed = true
                        AppLogger.w(TAG, "pushMissing: Upload fehlgeschlagen fuer txId=${tx.id}: HTTP ${resp.code()}")
                    }
                    // Fortschritt sichtbar machen: ein 2322er-Lauf dauert sonst minutenlang
                    // ohne jedes Lebenszeichen und sieht wie ein Haenger aus.
                    if ((index + 1) % PUSH_PROGRESS_EVERY == 0) {
                        AppLogger.i(TAG, "pushMissing: ${index + 1}/${unpushed.size} verarbeitet, $pushed hochgeladen")
                    }
                }
                return pushed
            }

            setPhase(prefs, PHASE_PUSH_TX)
            val pushed = pushMissing()
            logPhase(
                runId, PHASE_PUSH_TX, SyncLogRepository.DIRECTION_PUSH, "Buchungen (Upload)",
                pushed, true, "$pushed hochgeladen"
            )

            setPhase(prefs, PHASE_PULL_TX)
// Pull remote transactions - deduplicate against known remoteIds.
            // Die Seiten werden durchzaehlt und dabei sofort importiert, damit der Dialog
            // eine ECHTE Backend-Zahl zeigen kann und nicht nur die Groesse des ersten Abrufs.
            val existingRemoteIds = transactionRepo.getAllRemoteIds().toMutableSet()
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
                    if (page.code() == 401 || page.code() == 403) authFailed = true
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
            logPhase(
                runId, PHASE_PULL_TX, SyncLogRepository.DIRECTION_PULL, "Buchungen (Import)",
                pulled, pullComplete,
                if (pullComplete) "$pulled importiert, $remoteTxCount im Backend"
                else "$pulled importiert - Abruf unvollstaendig"
            )

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
                    setPhase(prefs, PHASE_PUSH_REPUSH)
                    val repushed = pushMissing()
                    AppLogger.i(TAG, "doWork: $repushed Buchungen nach zurueckgesetzten IDs erneut gesendet")
                    logPhase(
                        runId, PHASE_PUSH_REPUSH, SyncLogRepository.DIRECTION_PUSH,
                        "Buchungen (erneut)", repushed, true,
                        "$cleared IDs zurueckgesetzt, $repushed gesendet"
                    )
                } else {
                    AppLogger.i(TAG, "doWork: Repush angefordert, aber keine verwaisten IDs gefunden")
                    logPhase(
                        runId, PHASE_PUSH_REPUSH, SyncLogRepository.DIRECTION_PUSH,
                        "Buchungen (Repush)", 0, true, "angefordert, aber keine verwaisten IDs"
                    )
                }
            }

            // Konten nur zaehlen - es gibt noch kein Mapping lokal<->Backend (kein Feld dafuer).
            val accResp = api.getAccounts()
            var remoteAccCount = -1
            if (accResp.isSuccessful) {
                remoteAccCount = accResp.body()?.size ?: 0
            } else {
                httpFailures++
                if (accResp.code() == 401 || accResp.code() == 403) authFailed = true
                AppLogger.w(TAG, "doWork: Konten-Abruf fehlgeschlagen: HTTP ${accResp.code()}")
            }

            // Dashboard-Bewertungen ziehen (Back-Sync, US-004). Bewusst isoliert: ein Fehler
            // hier darf den bestehenden Sync nicht gefaehrden oder als HTTP-Fehler zaehlen.
            setPhase(prefs, PHASE_PULL_PATTERNS)
            try {
                pullPatternEvaluations(api)
                logPhase(
                    runId, PHASE_PULL_PATTERNS, SyncLogRepository.DIRECTION_PULL,
                    "Muster-Bewertungen", 0, true
                )
            } catch (e: Exception) {
                AppLogger.w(TAG, "patterns: Pull uebersprungen (${e.javaClass.simpleName}: ${e.message})")
                logPhase(
                    runId, PHASE_PULL_PATTERNS, SyncLogRepository.DIRECTION_PULL,
                    "Muster-Bewertungen", 0, false, "uebersprungen: ${e.javaClass.simpleName}"
                )
            }

            if (httpFailures > 0) {
                // Kein "OK": der Lauf war nicht fehlerfrei, der Backend-Stand gilt als unbekannt.
                if (authFailed) {
                    // Dauerhafter Fehler: Result.failure() statt retry(). Sonst wartet der
                    // Auftrag mit exponentiellem Backoff (bis 5 h) und der Zustand bleibt
                    // so lange "Fehler", ohne dass ein weiterer Versuch etwas aendert.
                    val reason = "Backend lehnt den API-Key ab (HTTP 401/403) - bitte in den Einstellungen pruefen"
                    markFailed(prefs, reason, null)
                    logRunFailed(reason)
                    return Result.failure()
                }
                val reason = "$httpFailures Abfrage(n) mit HTTP-Fehler beantwortet (davon $pushed erfolgreich hochgeladen)"
                markFailed(prefs, reason, null)
                logRunFailed(reason)
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
            logPhase(
                runId, PHASE_RUN, SyncLogRepository.DIRECTION_NONE, "Lauf abgeschlossen",
                remoteTxCount, true,
                "hochgeladen=$pushed, importiert=$pulled, Backend=$remoteTxCount Buchungen / $remoteAccCount Konten"
            )
            Result.success()
        } catch (e: java.net.SocketTimeoutException) {
            val reason = "Zeitüberschreitung bei $backendUrl - Server läuft nicht oder blockiert die Anfrage"
            markFailed(prefs, reason, e)
            logRunFailed(reason)
            Result.retry()
        } catch (e: java.net.UnknownHostException) {
            val reason = "Host aus $backendUrl nicht auflösbar - falsche URL oder falsches WLAN"
            markFailed(prefs, reason, e)
            logRunFailed(reason)
            Result.retry()
        } catch (e: java.net.ConnectException) {
            val reason = "Verbindung zu $backendUrl abgelehnt - läuft der PHP-Server?"
            markFailed(prefs, reason, e)
            logRunFailed(reason)
            Result.retry()
        } catch (e: java.io.IOException) {
            val reason = "Netzwerkfehler bei $backendUrl: ${e.javaClass.simpleName}: ${e.message}"
            markFailed(prefs, reason, e)
            logRunFailed(reason)
            Result.retry()
        } catch (e: Exception) {
            // Am 05.10.2026 war die eigentliche Ursache ein PHP-Fehler im Antwort-Body
            // (Fremdschluessel-Verstoss), die App meldete nur "MalformedJsonException" und
            // damit die Ursache versteckt. Der Roh-Body steht in den HTTP-Logzeilen darunter.
            val reason = "Backend-Sync fehlgeschlagen: ${e.javaClass.simpleName}: ${e.message}" +
                " (Rohantwort siehe HTTP-Logzeilen)"
            markFailed(prefs, reason, e)
            logRunFailed(reason)
            Result.retry()
        } finally {
            // Die Phase gehoert keinem Lauf mehr an, sobald er vorbei ist - sonst bliebe ein
            // Pfeil dauerhaft hervorgehoben und wuerde einen laufenden Sync behaupten.
            setPhase(prefs, null)
            runningSync.set(false)
        }
    }

    /**
     * Legt fehlende Konten im Backend an und liefert die Zuordnung lokale ID -> Backend-ID.
     *
     * Vorher gab es diese Zuordnung nicht ("es gibt kein Feld dafuer") und der Sync zaehlte
     * Konten nur. Damit war jeder Buchungs-Upload in ein leeres Backend ein
     * Fremdschluessel-Fehler (SQLSTATE 23000 / 1452).
     *
     * Die Zuordnung liegt in den SharedPreferences, nicht in einem DB-Feld - bewusst, um
     * fuer diese Reparatur keine Room-Migration anzufassen. Matching ueber Name+Waehrung;
     * ein spaeter umbenanntes Konto wuerde ein zweites Konto erzeugen.
     */
    private fun categoryKey(name: String, parent: Long?, level: Int): String = "$name|${parent ?: 0}|$level"

    /**
     * Kategorien wie Konten abgleichen. Ohne das loest jede Buchung mit einer lokalen
     * categoryId > 22 einen Fremdschluessel-Fehler aus, weil das Backend nur 22
     * vorinstallierte Kategorien mit den Ids 1..22 hat.
     */
    private suspend fun syncCategories(
        api: ApiService,
        prefs: SharedPreferences
    ): Map<Long, Long> {
        val local = categoryRepo.getAll()
        val map = HashMap<Long, Long>()

        val resp = api.getCategories()
        if (!resp.isSuccessful) {
            AppLogger.w(TAG, "syncCategories: Kategorien nicht abrufbar (HTTP ${resp.code()})")
            return map
        }
        val remote = resp.body().orEmpty()
        val byKey = HashMap<String, Long>()
        remote.forEach { dto ->
            byKey[categoryKey(dto.name, dto.parentCategoryId, dto.level)] = dto.id
        }
        AppLogger.i(TAG, "syncCategories: ${local.size} lokal, ${remote.size} im Backend")

        var created = 0
        listOf(1, 2, 3).forEach { level ->
            local.filter { it.level == level }.forEach { category ->
                // Wichtig: der Schluessel muss die Backend-ID des Elternteils nutzen, nicht die
                // lokale. Sonst trifft `Name|lokaleElternId|2` nie `Name|backendElternId|2`
                // und jede Ebene ab 2 wird doppelt angelegt (05.10.2026: 110 Kategorien
                // statt 22, davon 78 ohne Buchung).
                val parentBackend = category.parentCategoryId?.let { map[it] }
                if (category.parentCategoryId != null && parentBackend == null) {
                    AppLogger.w(TAG, "syncCategories: '${category.name}' uebersprungen - Elternkategorie nicht zugeordnet")
                    return@forEach
                }
                val key = categoryKey(category.name, parentBackend, category.level)
                val existing = byKey[key]
                if (existing != null) {
                    map[category.id] = existing
                    return@forEach
                }
                val created2 = api.createCategory(
                    CategoryDto(
                        name = category.name,
                        parentCategoryId = parentBackend,
                        color = category.color,
                        icon = category.icon,
                        pattern = category.pattern,
                        level = category.level,
                        isDefault = category.isDefault
                    )
                )
                if (created2.isSuccessful) {
                    val newId = created2.body()?.get("id") ?: 0L
                    if (newId > 0) {
                        byKey[key] = newId
                        map[category.id] = newId
                        created++
                        AppLogger.i(TAG, "syncCategories: Kategorie angelegt '${category.name}' -> id=$newId")
                    }
                } else {
                    AppLogger.w(TAG, "syncCategories: Kategorie '${category.name}' nicht angelegt (HTTP ${created2.code()})")
                }
            }
        }
        AppLogger.i(TAG, "syncCategories: $created neu angelegt, ${map.size}/${local.size} zugeordnet")

        // Spiegelprobe: Kategorien im Backend, die die App nicht kennt. Umgekehrter Fall zu den
        // verwaisten Buchungs-IDs - dort wird gemeldet, was lokal zu viel ist, hier was im Backend
        // zu viel ist. Genau so sind am 05.10.2026 die 22 deutschen Seed-Kategorien entstanden:
        // sie sind nie benutzt worden, aber auch nie entfernt worden, weil niemand sie je pruefte.
        val known = map.values.toSet()
        val unknown = remote.filter { it.id !in known }
        if (unknown.isNotEmpty()) {
            AppLogger.w(
                TAG,
                "syncCategories: ${unknown.size} Backend-Kategorie(n) unbekannt in der App (nicht " +
                    "benutzt, aber auch nicht bereinigt): " +
                    unknown.joinToString(", ") { "#${it.id} ${it.name}" }.take(400)
            )
            prefs.edit().putInt(PREF_UNKNOWN_REMOTE_CATEGORIES, unknown.size).apply()
        } else {
            prefs.edit().putInt(PREF_UNKNOWN_REMOTE_CATEGORIES, 0).apply()
        }

        prefs.edit().putString("category_id_map", map.entries.joinToString(";") { "${it.key}=${it.value}" }).apply()
        return map
    }

    private suspend fun syncAccounts(
        api: ApiService,
        prefs: SharedPreferences
    ): Map<Long, Long> {
        val local = accountRepo.getAll()
        val map = HashMap<Long, Long>()

        val resp = api.getAccounts()
        if (!resp.isSuccessful) {
            AppLogger.w(TAG, "syncAccounts: Konten nicht abrufbar (HTTP ${resp.code()})")
            return map
        }
        val remote = resp.body().orEmpty()
        val byKey = HashMap<String, AccountDto>()
        remote.forEach { byKey[accountKey(it.name, it.currency)] = it }
        AppLogger.i(TAG, "syncAccounts: ${local.size} lokal, ${remote.size} im Backend")

        // Eltern vor Kindern: ein Kind braucht die Backend-ID des Elternkontos.
        for (account in local.sortedBy { if (it.parentAccountId == null) 0 else 1 }) {
            val key = accountKey(account.name, account.currency)
            val existing = byKey[key]
            if (existing != null) {
                map[account.id] = existing.id
                continue
            }
            val parent = account.parentAccountId?.let { map[it] }
            val created = api.createAccount(
                AccountDto(
                    name = account.name,
                    type = account.type.name,
                    balance = account.balance,
                    currency = account.currency,
                    color = account.color,
                    icon = account.icon,
                    parentAccountId = parent,
                    isVirtual = account.isVirtual,
                    bankCode = account.bankCode,
                    iban = account.iban
                )
            )
            val body = created.body()
            val newId = body?.get("id")?.toLong()
            if (created.isSuccessful && newId != null) {
                map[account.id] = newId
                byKey[key] = AccountDto(id = newId, name = account.name, currency = account.currency)
                AppLogger.i(TAG, "syncAccounts: Konto angelegt '${account.name}' -> id=$newId")
            } else {
                AppLogger.w(
                    TAG,
                    "syncAccounts: Konto '${account.name}' nicht angelegt (HTTP ${created.code()})"
                )
            }
        }
        saveAccountMap(prefs, map)
        AppLogger.i(TAG, "syncAccounts: ${map.size}/${local.size} Konten zugeordnet")
        return map
    }

    private fun accountKey(name: String, currency: String) = "$name|$currency"

    private fun saveAccountMap(prefs: SharedPreferences, map: Map<Long, Long>) {
        val text = map.entries.joinToString(",") { "${it.key}:${it.value}" }
        prefs.edit().putString("account_id_map", text).apply()
    }

    /**
     * Zieht die Muster-Bewertungen des Dashboards und legt sie lokal ab (US-004).
     *
     * Read-only und additiv: Es wird nichts an Buchungen geschrieben. Nur die Bewertungen
     * (Wichtigkeit/Kuerzbarkeit/Notiz/Kategorie-Override) wandern in `pattern_evaluations`.
     * Der Kategorie-Override ist im Dashboard ein PFAD-String ("A > B"); hier wird er auf
     * eine lokale categoryId aufgeloest - findet sich keine, bleibt der Override leer
     * (die Bewertung selbst wird trotzdem uebernommen).
     */
    private suspend fun pullPatternEvaluations(api: ApiService): Int {
        val version = PatternEvaluationSuggestion.MATCHER_VERSION
        val resp = api.getPatterns(version)
        if (!resp.isSuccessful) {
            AppLogger.w(TAG, "patterns: Abruf fehlgeschlagen (HTTP ${resp.code()})")
            return 0
        }
        val dtos = resp.body()?.patterns.orEmpty()
        if (dtos.isEmpty()) return 0

        val pathToId = buildCategoryPathIndex()
        val rows = dtos
            .filter { it.matcherVersion == version }
            .mapNotNull { dto ->
                val key = dto.musterKey.trim()
                if (key.isEmpty()) return@mapNotNull null
                PatternEvaluation(
                    patternKey = key,
                    matcherVersion = version,
                    importance = dto.wichtigkeit.coerceIn(0, 5),
                    cuttability = dto.kuerzbarkeit.coerceIn(0, 5),
                    note = dto.notiz,
                    categoryOverrideId = dto.kategorieOverride?.let { pathToId[normalizeCategoryPath(it)] },
                    updatedAt = dto.updatedAt,
                    source = PatternEvaluation.SOURCE_SERVER
                )
            }
        val applied = patternEvalRepo.merge(rows, version)
        AppLogger.i(TAG, "patterns: ${dtos.size} vom Server, ${rows.size} passend, $applied uebernommen")
        flagRemovedOverrides(rows.map { it.patternKey }.toSet(), version)
        return applied
    }

    /**
     * US-004-Ruecknahme: Server-Overrides, deren Schluessel im aktuellen Abzug fehlt
     * (im Dashboard geloescht/geleert) und deren Kategorien die App angewendet hat,
     * werden nur **vorgemerkt** - zurueckgesetzt wird erst nach Nutzerbestaetigung
     * (siehe Muster-Screen). Ein Fehler hier darf den Sync nicht gefaehrden.
     */
    private suspend fun flagRemovedOverrides(serverKeys: Set<String>, version: String) {
        try {
            val removed = patternOverrideRepo.removedServerOverrideKeys(serverKeys, version)
                .filter { patternOverrideRepo.countApplied(it) > 0 }
            if (removed.isEmpty()) return
            val prefs = applicationContext.getSharedPreferences("mybudgets_prefs", Context.MODE_PRIVATE)
            val existing = prefs.getStringSet(PREF_PENDING_OVERRIDE_RESETS, emptySet()) ?: emptySet()
            val merged = java.util.HashSet(existing).apply { addAll(removed) }
            prefs.edit().putStringSet(PREF_PENDING_OVERRIDE_RESETS, merged).apply()
            AppLogger.i(TAG, "patterns: ${removed.size} angewendete Override(s) entfernt - Ruecknahme vorgemerkt")
        } catch (e: Exception) {
            AppLogger.w(TAG, "patterns: Ruecknahme-Erkennung fehlgeschlagen (${e.javaClass.simpleName}: ${e.message})")
        }
    }

    /** Pfad-Index der lokalen Kategorien: normalisierter Pfad UND Blattname -> categoryId. */
    private suspend fun buildCategoryPathIndex(): Map<String, Long> {
        val cats = categoryRepo.getAll()
        val byId = cats.associateBy { it.id }
        val index = HashMap<String, Long>()
        for (c in cats) {
            val parts = ArrayList<String>()
            var cur: Category? = c
            var guard = 0
            while (cur != null && guard++ < 20) {
                parts.add(0, cur.name)
                val parentId: Long? = cur.parentCategoryId
                cur = parentId?.let { byId[it] }
            }
            index[normalizeCategoryPath(parts.joinToString(" > "))] = c.id
            index.putIfAbsent(normalizeCategoryPath(c.name), c.id)
        }
        return index
    }

    private fun normalizeCategoryPath(path: String): String =
        path.trim().lowercase().replace(Regex("\\s*>\\s*"), " > ").replace(Regex("\\s+"), " ")

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
