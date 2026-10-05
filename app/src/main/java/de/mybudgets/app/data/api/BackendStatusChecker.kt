package de.mybudgets.app.data.api

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import de.mybudgets.app.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.PortUnreachableException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException

private const val TAG = "BackendStatusChecker"
private const val CHECK_INTERVAL_MS = 30_000L
private const val PREFS = "mybudgets_prefs"
private const val PREF_LAST_DIAGNOSIS = "last_backend_diagnosis"
private const val PREF_LAST_DIAGNOSIS_TS = "last_backend_diagnosis_ts"

enum class BackendStatus { GRAY, GREEN, RED }

/** Every distinguishable reason why the backend check can fail. */
enum class BackendProblem {
    NONE, DISABLED, NO_URL, NO_NETWORK, NOT_ON_WIFI, CLEARTEXT_BLOCKED, DNS_FAILED,
    TIMEOUT, CONNECTION_REFUSED, HOST_UNREACHABLE, SSL_ERROR, HTTP_UNAUTHORIZED,
    SERVER_BASIC_AUTH, HTTP_REDIRECT, HTTP_NOT_FOUND, HTTP_BAD_GATEWAY, HTTP_SERVER_ERROR,
    HTTP_OTHER, UNKNOWN
}

/**
 * Result of one backend check, detailed enough to explain a red status lamp without
 * attaching a debugger. [report] is the human-readable multi-line version.
 */
data class BackendDiagnosis(
    val status: BackendStatus,
    val problem: BackendProblem,
    val headline: String,
    val hint: String,
    val url: String,
    val endpoint: String,
    val httpCode: Int?,
    val latencyMs: Long,
    val transport: String,
    val localIps: String,
    val resolvedIps: String,
    val apiKeyInfo: String,
    val debuggable: Boolean,
    val responseSnippet: String,
    val timestamp: Long
) {
    fun report(): String = buildString {
        appendLine("Status: $status  (${formatTimestamp(timestamp)})")
        appendLine("Ursache: $headline")
        appendLine("Backend-URL: $url")
        appendLine("Geprüfter Endpunkt: $endpoint")
        appendLine("HTTP-Status: ${httpCode?.toString() ?: "keine Antwort"}")
        appendLine("Dauer: $latencyMs ms")
        appendLine("Netzwerk: $transport")
        appendLine("Eigene IP-Adressen: $localIps")
        appendLine("Aufgelöster Host: $resolvedIps")
        appendLine("API-Key: $apiKeyInfo")
        appendLine("App ist Debug-Build: $debuggable")
        if (responseSnippet.isNotBlank()) appendLine("Antwort des Servers: $responseSnippet")
        appendLine("Empfehlung: $hint")
    }
}

internal fun formatTimestamp(ts: Long): String =
    java.text.SimpleDateFormat("dd.MM. HH:mm:ss", java.util.Locale.GERMANY).format(java.util.Date(ts))

/**
 * Polls the backend every [CHECK_INTERVAL_MS] and publishes both a coarse
 * [BackendStatus] for the lamp and a detailed [BackendDiagnosis] for the UI.
 */
@Singleton
class BackendStatusChecker @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val _status = MutableStateFlow(BackendStatus.GRAY)
    val status: StateFlow<BackendStatus> = _status.asStateFlow()

    private val _diagnosis = MutableStateFlow(restoredDiagnosis())
    val diagnosis: StateFlow<BackendDiagnosis> = _diagnosis.asStateFlow()

    /**
     * true, solange [doCheck] laeuft. Das Laempchen blinkt in diesem Fenster, damit ein
     * Check, der mehrere Sekunden dauert (Timeout bis 10 s), nicht wie ein eingefrorener
     * Zustand wirkt.
     */
    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var loopJob: Job? = null

    private fun prefs(): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "backend_url" || key == "api_key" || key == "offline_mode") {
            AppLogger.i(TAG, "Einstellung '$key' geändert - Statusprüfung wird ausgelöst")
            triggerCheck()
        }
    }

    /**
     * Redirects are deliberately NOT followed: a 302 to a login page is a *cause* of
     * the red lamp, and following it would hide that behind whatever the login page
     * happens to return. Same reasoning for the auth-realm header.
     */
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    fun start() {
        prefs().registerOnSharedPreferenceChangeListener(prefsListener)
        if (loopJob?.isActive == true) return
        loopJob = scope.launch {
            while (true) {
                doCheck()
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        prefs().unregisterOnSharedPreferenceChangeListener(prefsListener)
        loopJob?.cancel()
        loopJob = null
    }

    fun triggerCheck() {
        scope.launch { doCheck() }
    }

    /** Runs a check and returns its detailed result - used by the status-lamp dialog. */
    suspend fun checkNow(): BackendDiagnosis = doCheck()

    // ─── Check ───────────────────────────────────────────────────────────────────

    private suspend fun doCheck(): BackendDiagnosis {
        _checking.value = true
        return try {
            performCheck()
        } finally {
            _checking.value = false
        }
    }

    private suspend fun performCheck(): BackendDiagnosis {
        val offline = prefs().getBoolean("offline_mode", true)
        val url = normalizeBaseUrl((prefs().getString("backend_url", "") ?: "").trim())
        val key = (prefs().getString("api_key", "") ?: "").trim()
        val keyInfo = if (key.isEmpty()) "nicht gesetzt" else "gesetzt (${key.length} Zeichen)"
        val debuggable = isDebuggable()

        AppLogger.d(
            TAG,
            "Prüfung startet: offline=$offline, url='$url', key=$keyInfo, debug=$debuggable"
        )

        if (offline || url.isBlank()) {
            val d = diagnose(
                status = BackendStatus.GRAY,
                problem = if (offline) BackendProblem.DISABLED else BackendProblem.NO_URL,
                headline = if (offline) "Sync ist deaktiviert (Offline-Modus)" else "Keine Backend-URL eingetragen",
                hint = if (offline) {
                    "In den Einstellungen den Offline-Modus ausschalten, damit das Lämpchen den Server prüft."
                } else {
                    "In den Einstellungen unter Backend die URL eintragen, z. B. http://192.168.178.40/apps/finn/"
                },
                url = url,
                endpoint = "-",
                httpCode = null,
                latency = 0L,
                net = networkInfo(),
                resolved = "-",
                keyInfo = keyInfo,
                debuggable = debuggable
            )
            return publish(d)
        }

        val net = networkInfo()
        AppLogger.i(TAG, "Netzwerklage: ${net.summary} | eigene IPs: ${net.ips}")

        if (!net.isActive) {
            val d = diagnose(
                BackendStatus.RED, BackendProblem.NO_NETWORK,
                "Kein aktives Netzwerk - das Gerät ist offline",
                "WLAN oder mobile Daten einschalten. Die Prüfung läuft automatisch weiter, sobald Netz da ist.",
                url, "$url/version", null, 0L, net, "-", keyInfo, debuggable
            )
            return publish(d)
        }

        if (url.startsWith("http://", ignoreCase = true) && net.transport == "Mobilfunk") {
            AppLogger.w(
                TAG,
                "Hinweis: Backend liegt im lokalen Netz, das Gerät nutzt aber Mobilfunk - " +
                    "die Verbindung kann nur über WLAN funktionieren."
            )
        }

        val host = hostOf(url)
        val resolved = resolveHost(host)
        if (resolved.startsWith("FEHLER")) {
            val d = diagnose(
                BackendStatus.RED, BackendProblem.DNS_FAILED,
                "Hostname '$host' lässt sich nicht auflösen",
                "Entweder Tippfehler in der URL oder das Handy ist im falschen Netz. " +
                    "Bei einer IP-Adresse im WLAN ist keine Namensauflösung nötig - nutze dann z. B. die IP des Servers.",
                url, "$url/version", null, 0L, net, resolved, keyInfo, debuggable
            )
            return publish(d)
        }
        AppLogger.d(TAG, "DNS: $host -> $resolved")

        val cleartext = url.startsWith("http://", ignoreCase = true)
        if (cleartext && !debuggable) {
            AppLogger.e(
                TAG,
                "Klartext-HTTP ist in dieser App gesperrt (Release-Build). Jede Anfrage an $url würde scheitern."
            )
        }

        val endpoint = url.trimEnd('/') + "/version"
        val started = System.currentTimeMillis()
        var httpCode: Int? = null
        var failure: Throwable? = null
        var snippet = ""
        var authRealm: String? = null
        var location: String? = null

        try {
            withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url(endpoint)
                    .header("X-API-Key", key)
                    .header("Accept", "application/json")
                    .get()
                    .build()
                AppLogger.d(TAG, "GET $endpoint (Header: X-API-Key, key=$keyInfo)")
                httpClient.newCall(request).execute().use { response ->
                    httpCode = response.code
                    authRealm = response.header("WWW-Authenticate")
                    location = response.header("Location")
                    val body = runCatching { response.body?.string() }.getOrNull()
                    snippet = snippetOf(body)
                    AppLogger.d(
                        TAG,
                        "Antwort HTTP ${response.code} ${response.message} in ${System.currentTimeMillis() - started} ms" +
                            (authRealm?.let { " - WWW-Authenticate: $it" } ?: "") +
                            (location?.let { " - Location: $it" } ?: "") +
                            (body?.let { " - Body: ${it.take(200)}" } ?: "")
                    )
                }
            }
        } catch (t: Throwable) {
            failure = t
            AppLogger.w(TAG, "GET $endpoint fehlgeschlagen: ${t.javaClass.simpleName}: ${t.message}", t)
        }

        val latency = System.currentTimeMillis() - started
        val d = classify(url, endpoint, httpCode, latency, failure, net, resolved, keyInfo, debuggable, snippet, authRealm, location)
        return publish(d)
    }

    /**
     * The first bytes of a response are what separate "PHP said Unauthorized" from
     * "the web server demanded a browser login" - two failures that look identical
     * in the status code alone.
     */
    private fun snippetOf(body: String?): String {
        if (body.isNullOrBlank()) return ""
        val flat = body.replace(Regex("\\s+"), " ").trim()
        val looksJson = flat.startsWith("{") || flat.startsWith("[")
        val head = if (looksJson) flat else flat.take(120)
        return if (flat.length > head.length) "$head ..." else head
    }

    private fun classify(
        url: String,
        endpoint: String,
        httpCode: Int?,
        latency: Long,
        failure: Throwable?,
        net: NetInfo,
        resolved: String,
        keyInfo: String,
        debuggable: Boolean,
        snippet: String = "",
        authRealm: String? = null,
        location: String? = null
    ): BackendDiagnosis {
        if (failure != null) {
            val problem: BackendProblem
            val headline: String
            val hint: String
            when {
                hasMessage(failure, "Cleartext HTTP traffic") -> {
                    problem = BackendProblem.CLEARTEXT_BLOCKED
                    headline = "Klartext-HTTP ist für diese App gesperrt (Cleartext nicht erlaubt)"
                    hint = "Entweder HTTPS verwenden oder eine Debug-Version der App installieren " +
                        "(nur dort erlaubt network_security_config.xml das http://). " +
                        "Häufigster Grund: eine Release-Version statt des Debug-APK installiert."
                }
                failure is UnknownHostException -> {
                    problem = BackendProblem.DNS_FAILED
                    headline = "Hostname nicht auflösbar: ${failure.message ?: "unbekannt"}"
                    hint = "URL prüfen. Im eigenen WLAN besser die IP-Adresse des Servers verwenden, " +
                        "damit kein DNS nötig ist."
                }
                failure is SocketTimeoutException || failure is InterruptedIOException -> {
                    problem = BackendProblem.TIMEOUT
                    headline = "Zeitüberschreitung nach $latency ms"
                    hint = "Server antwortet nicht. Läuft der PHP-Server wirklich, und hängt das WLAN " +
                        " eventuell hinter einem Gast-Netz ohne Client-Isolation?"
                }
                failure is ConnectException -> {
                    problem = BackendProblem.CONNECTION_REFUSED
                    headline = "Verbindung abgelehnt (nichts lauscht auf diesem Port)"
                    hint = "Server läuft nicht oder falscher Port. Prüfen: lauscht der PHP-Server, " +
                        "und stimmt Port/Pfad in der URL?"
                }
                failure is NoRouteToHostException || failure is PortUnreachableException -> {
                    problem = BackendProblem.HOST_UNREACHABLE
                    headline = "Zielhost nicht erreichbar (keine Route im Netz)"
                    hint = "Das Gerät ist in einem anderen Subnetz als der Server oder der Server ist ausgeschaltet."
                }
                failure is SSLException -> {
                    problem = BackendProblem.SSL_ERROR
                    headline = "TLS/SSL-Fehler: ${failure.message ?: "unbekannt"}"
                    hint = "Zertifikat oder Protokollversion des Servers prüfen."
                }
                failure is FileNotFoundException -> {
                    problem = BackendProblem.HTTP_NOT_FOUND
                    headline = "Endpunkt nicht gefunden (404)"
                    hint = "Pfad in der Backend-URL prüfen. Erwartet wird das Verzeichnis mit api.php, " +
                        "nicht die api.php selbst und nicht mit /version am Ende."
                }
                failure is IOException -> {
                    problem = BackendProblem.UNKNOWN
                    headline = "Netzwerkfehler: ${failure.message ?: failure.javaClass.simpleName}"
                    hint = "Unbekannter Netzwerkfehler - Details im Fehlerprotokoll (Stacktrace)."
                }
                else -> {
                    problem = BackendProblem.UNKNOWN
                    headline = "Unerwarteter Fehler: ${failure.javaClass.simpleName}: ${failure.message}"
                    hint = "Details im Fehlerprotokoll (Stacktrace)."
                }
            }
            return diagnose(
                BackendStatus.RED, problem, headline, hint,
                url, endpoint, httpCode, latency, net, resolved, keyInfo, debuggable
            )
        }
        val code = httpCode ?: 0
        val htmlBody = snippet.isNotBlank() && !snippet.startsWith("{") && !snippet.startsWith("[")
        return when {
            // The web server (Apache/nginx) demanded a browser login. The X-API-Key
            // never even reached api.php - a real and easily missed cause.
            code == 401 && authRealm != null -> diagnose(
                BackendStatus.RED, BackendProblem.SERVER_BASIC_AUTH,
                "HTTP 401 von $authRealm - der Webserver verlangt eine Benutzeranmeldung",
                "Das ist keine App-Sache: Apache/nginx schickt die Anmeldeseite, bevor api.php läuft. " +
                    "Bei Apache ist das die Basic-Auth-Regel in der .htaccess, bei nginx eine " +
                    "auth_basic-Direktive. Zwei Lösungen: den direkten Port des PHP-Servers nehmen " +
                    "(der umgeht die Webserver-Regel) oder den Pfad in der .htaccess/nginx von " +
                    "der Anmeldung ausnehmen.",
                url, endpoint, code, latency, net, resolved, keyInfo, debuggable, snippet
            )
            code in 300..399 -> diagnose(
                BackendStatus.RED, BackendProblem.HTTP_REDIRECT,
                "HTTP $code - der Server leitet um" + (location?.let { " nach $it" } ?: ""),
                "Die Adresse landet nicht bei der API, sondern irgendwo anders (Startseite, Login). " +
                    "In der Backend-URL steht sehr wahrscheinlich ein falscher Pfad oder Port. " +
                    "Prüfen, ob der PHP-Server direkt erreichbar ist.",
                url, endpoint, code, latency, net, resolved, keyInfo, debuggable, snippet
            )
            code in 200..299 -> diagnose(
                BackendStatus.GREEN, BackendProblem.NONE,
                "Server antwortet mit HTTP $code",
                "Alles in Ordnung - die Verbindung zum Backend steht.",
                url, endpoint, code, latency, net, resolved, keyInfo, debuggable, snippet
            )
            code == 401 || code == 403 -> diagnose(
                BackendStatus.RED, BackendProblem.HTTP_UNAUTHORIZED,
                "HTTP $code - der API-Key wird vom Backend abgelehnt",
                "Die URL ist richtig, der Schlüssel nicht. Key in config.php bzw. config.local.php " +
                    "des Servers und in den App-Einstellungen vergleichen - inkl. Leerzeichen und " +
                    "Groß-/Kleinschreibung. Das Backend meldet sich mit {\"error\":\"Unauthorized\"}.",
                url, endpoint, code, latency, net, resolved, keyInfo, debuggable, snippet
            )
            code == 404 -> diagnose(
                BackendStatus.RED, BackendProblem.HTTP_NOT_FOUND,
                "HTTP 404 - unter dieser Adresse liegt keine API",
                "Zwei typische Ursachen: (1) In der URL steht ein zusätzliches '/api.php' - das " +
                    "gehört nicht in die Adresse, die App hängt '/version' selbst an. (2) Es ist der " +
                    "falsche Server/Pfad. Funktionierende Form ist z. B. http://IP:8089/ ohne Slash am Ende.",
                url, endpoint, code, latency, net, resolved, keyInfo, debuggable, snippet
            )
            code in 500..599 -> diagnose(
                BackendStatus.RED, BackendProblem.HTTP_SERVER_ERROR,
                "HTTP $code - Serverfehler",
                "Der Server ist erreichbar, läuft aber in einen PHP-Fehler. Server-Log (error_log) prüfen.",
                url, endpoint, code, latency, net, resolved, keyInfo, debuggable, snippet
            )
            code == 0 -> diagnose(
                BackendStatus.RED, BackendProblem.UNKNOWN,
                "Kein HTTP-Status erhalten",
                "Der Server hat nicht geantwortet. Netzwerk und Adresse prüfen.",
                url, endpoint, code, latency, net, resolved, keyInfo, debuggable, snippet
            )
            else -> diagnose(
                BackendStatus.RED, BackendProblem.HTTP_OTHER,
                "Unerwarteter HTTP-Status $code" + if (htmlBody) " (Antwort war HTML, kein JSON)" else "",
                if (htmlBody) {
                    "Der antwortende Server ist nicht die PHP-API - die Adresse zeigt auf eine " +
                        "Webseite. URL und Port prüfen."
                } else {
                    "Der Server antwortet, aber nicht mit 2xx. Response im Server-Log ansehen."
                },
                url, endpoint, code, latency, net, resolved, keyInfo, debuggable, snippet
            )
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private fun diagnose(
        status: BackendStatus,
        problem: BackendProblem,
        headline: String,
        hint: String,
        url: String,
        endpoint: String,
        httpCode: Int?,
        latency: Long,
        net: NetInfo,
        resolved: String,
        keyInfo: String,
        debuggable: Boolean,
        snippet: String = ""
    ): BackendDiagnosis = BackendDiagnosis(
        status = status, problem = problem, headline = headline, hint = hint,
        url = url.ifEmpty { "(keine URL)" }, endpoint = endpoint, httpCode = httpCode,
        latencyMs = latency, transport = net.summary, localIps = net.ips, resolvedIps = resolved,
        apiKeyInfo = keyInfo, debuggable = debuggable, responseSnippet = snippet,
        timestamp = System.currentTimeMillis()
    )

    private fun publish(d: BackendDiagnosis): BackendDiagnosis {
        val previous = _diagnosis.value
        val changed = previous.status != d.status || previous.problem != d.problem
        _status.value = d.status
        _diagnosis.value = d

        // Every check leaves one compact trace line; the full report is logged only
        // when something actually changed - otherwise a red lamp would fill the log
        // console with 120 identical errors per hour and hide the real cause.
        AppLogger.d(
            TAG,
            "Check: ${d.status} | ${d.headline} | HTTP ${d.httpCode ?: "-"} | ${d.latencyMs} ms | ${d.url}"
        )
        if (changed) {
            when (d.status) {
                BackendStatus.RED -> AppLogger.e(TAG, "Statuswechsel auf ${d.status}:\n${d.report()}")
                BackendStatus.GREEN -> AppLogger.i(TAG, "Statuswechsel auf ${d.status}:\n${d.report()}")
                BackendStatus.GRAY -> AppLogger.i(TAG, "Statuswechsel auf ${d.status}:\n${d.report()}")
            }
            persist(d)
        }
        return d
    }

    private fun persist(d: BackendDiagnosis) {
        runCatching {
            prefs().edit()
                .putString(PREF_LAST_DIAGNOSIS, "${d.problem} | ${d.headline} | HTTP ${d.httpCode ?: "-"}")
                .putLong(PREF_LAST_DIAGNOSIS_TS, d.timestamp)
                .apply()
        }.onFailure { AppLogger.w(TAG, "Letzte Diagnose konnte nicht gespeichert werden: ${it.message}") }
    }

    private fun restoredDiagnosis(): BackendDiagnosis {
        val stored = prefs().getString(PREF_LAST_DIAGNOSIS, "") ?: ""
        return BackendDiagnosis(
            status = BackendStatus.GRAY,
            problem = BackendProblem.NONE,
            headline = if (stored.isBlank()) {
                "Noch keine Prüfung gelaufen"
            } else {
                "Letzte bekannte Ursache (vor dem App-Neustart): $stored"
            },
            hint = "Lämpchen antippen für eine sofortige Prüfung mit Detailauskunft.",
            url = (prefs().getString("backend_url", "") ?: "").ifEmpty { "(keine URL)" },
            endpoint = "-", httpCode = null, latencyMs = 0L,
            transport = networkInfo().summary, localIps = "-", resolvedIps = "-",
            apiKeyInfo = if ((prefs().getString("api_key", "") ?: "").isEmpty()) "nicht gesetzt" else "gesetzt",
            debuggable = isDebuggable(),
            responseSnippet = "",
            timestamp = prefs().getLong(PREF_LAST_DIAGNOSIS_TS, 0L).takeIf { it > 0 } ?: System.currentTimeMillis()
        )
    }

    private fun hostOf(url: String): String = runCatching {
        java.net.URI(if (url.contains("://")) url else "http://$url").host
    }.getOrNull()?.trim('[', ']').orEmpty().ifEmpty { "(unbekannt)" }

    private fun resolveHost(host: String): String = runCatching {
        java.net.InetAddress.getAllByName(host).joinToString(", ") { it.hostAddress.orEmpty() }
    }.getOrElse { e -> "FEHLER: ${e.javaClass.simpleName}: ${e.message}" }

    private fun hasMessage(t: Throwable, needle: String): Boolean {
        var cur: Throwable? = t
        while (cur != null) {
            if (cur.message?.contains(needle, ignoreCase = true) == true) return true
            cur = cur.cause.takeIf { it !== cur }
        }
        return false
    }

    private fun isDebuggable(): Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    /**
     * Current network situation, split into a human-readable [summary] and the raw
     * [ips] of the device - the IPs answer "am falschen WLAN?" without guesswork.
     */
    private fun networkInfo(): NetInfo {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return NetInfo("System lieferte keinen ConnectivityManager", "-", isActive = true, transport = "unbekannt")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            @Suppress("DEPRECATION")
            val info = cm.activeNetworkInfo
            return if (info != null && info.isConnected) {
                NetInfo("${info.typeName} - aktiv", "-", isActive = true, transport = info.typeName)
            } else {
                NetInfo("kein Netz - nicht verbunden", "-", isActive = false, transport = "kein Netz")
            }
        }
        val network: Network? = cm.activeNetwork
            ?: return NetInfo("kein Netz - nicht verbunden", "-", isActive = false, transport = "kein Netz")
        val caps = cm.getNetworkCapabilities(network)
            ?: return NetInfo("Netz aktiv, Fähigkeiten unbekannt", "-", isActive = true, transport = "unbekannt")
        val transports = buildList {
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add("WLAN")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add("Ethernet")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add("Mobilfunk")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) add("VPN")
        }
        val transport = if (transports.isEmpty()) "unbekannt" else transports.joinToString("+")
        val links = cm.getLinkProperties(network)?.let { linkAddresses(it) }.orEmpty()
        val validated = if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
            "Internet bestätigt"
        } else {
            "Internet unbestätigt"
        }
        val summary = "$transport - aktiv, $validated"
        return NetInfo(summary, links.joinToString(", ").ifEmpty { "-" }, isActive = true, transport = transport)
    }

    private data class NetInfo(
        val summary: String,
        val ips: String,
        val isActive: Boolean,
        val transport: String
    )

    private fun linkAddresses(props: LinkProperties): List<String> =
        props.linkAddresses.map { it.address.hostAddress.orEmpty() }.filter { it.isNotBlank() }
}
