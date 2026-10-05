package de.mybudgets.app.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

enum class LogLevel(val label: String) {
    DEBUG("D"), INFO("I"), WARN("W"), ERROR("E")
}

data class LogEntry(
    val id: Long,
    val timestamp: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val throwable: Throwable? = null
)

/**
 * In-app logger that writes to Android Logcat, an in-memory ring buffer and - after
 * [installFilePersistence] was called - a rolling log file in the app-internal storage.
 *
 * The in-memory buffer feeds the live log console. The file is what makes problems
 * diagnosable *after* the fact: entries survive an app restart, so a status lamp that
 * has been red since yesterday can still be explained.
 *
 * Use [export] to get all log entries as a plain-text string (e.g. to share via Intent).
 */
object AppLogger {

    private const val TAG = "AppLogger"

    private const val MAX_ENTRIES = 3000
    private const val MAX_FILE_BYTES = 512L * 1024L
    private const val LOG_FILE_NAME = "mybudgets.log"
    private const val TS_PATTERN = "yyyy-MM-dd HH:mm:ss.SSS"

    private const val LOG_PREFS = "mybudgets_logger"
    private const val KEY_VERSION_CODE = "version_code"

    /** Trennzeichen zwischen Nachricht und Stacktrace - kann in Logs nicht vorkommen. */
    private const val THR_SEP = "@@THR@@"

    /** Vom Log erzeugter Text, der das Trennzeichen nachahmen will, wird entschärft. */
    private const val THR_SEP_ESCAPED = "@@th@@"

    private val counter = AtomicLong(0)
    private val buffer = CopyOnWriteArrayList<LogEntry>()

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    private val fileLock = Any()
    private val fileExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "AppLogger-file").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }
    }

    @Volatile
    private var logFile: File? = null

    @Volatile
    private var filePersistenceReady = false

    // ─── Public logging API ──────────────────────────────────────────────────────

    fun d(tag: String, msg: String, t: Throwable? = null) = append(LogLevel.DEBUG, tag, msg, t)
    fun i(tag: String, msg: String, t: Throwable? = null) = append(LogLevel.INFO,  tag, msg, t)
    fun w(tag: String, msg: String, t: Throwable? = null) = append(LogLevel.WARN,  tag, msg, t)
    fun e(tag: String, msg: String, t: Throwable? = null) = append(LogLevel.ERROR, tag, msg, t)

    fun clear() {
        buffer.clear()
        _entries.value = emptyList()
        logFile?.let { file ->
            fileExecutor.execute {
                runCatching { synchronized(fileLock) { file.writeText("") } }
                    .onFailure { Log.w(TAG, "Logdatei konnte nicht geleert werden", it) }
            }
        }
    }

    /**
     * Activates rolling file logging and restores the entries of previous sessions.
     * Must be called once from Application.onCreate() - as early as possible, so that
     * early startup problems are recorded as well.
     *
     * [currentVersionCode] entscheidet ueber die Altlasten: Wurde die App aktualisiert,
     * gehoeren die alten Eintraege zu einem anderen Programmstand. Ein Fehler, der in
     * einem frueheren Stand passiert ist und nach dem Update scheinbar noch dasteht,
     * ist die haeufigste Fehlinterpretation in der Log-Konsole - deshalb wird die
     * Datei bei jedem Versionswechsel geleert statt weitergeführt.
     */
    fun installFilePersistence(context: Context, currentVersionCode: Int = 0) {
        if (filePersistenceReady) return
        runCatching {
            val dir = File(context.filesDir, "logs").apply { mkdirs() }
            val file = File(dir, LOG_FILE_NAME)
            val marker = context.getSharedPreferences(LOG_PREFS, Context.MODE_PRIVATE)
            val lastSeen = marker.getInt(KEY_VERSION_CODE, -1)
            val updated = lastSeen != -1 && lastSeen != currentVersionCode
            synchronized(fileLock) {
                if (updated) {
                    file.writeText("")
                } else {
                    trimIfOversized(file)
                    loadFromFile(file)
                }
            }
            marker.edit().putInt(KEY_VERSION_CODE, currentVersionCode).apply()
            logFile = file
            filePersistenceReady = true
            val restored = buffer.size
            i(
                TAG,
                if (updated) {
                    "Fehlerprotokoll aktiv: App wurde von Version $lastSeen auf $currentVersionCode " +
                        "aktualisiert - das alte Protokoll wurde verworfen, es gilt nur der aktuelle Stand."
                } else {
                    "Fehlerprotokoll aktiv: $restored Einträge aus früheren Sitzungen geladen, " +
                        "Datei ${file.absolutePath} (${file.length() / 1024} KB)"
                }
            )
        }.onFailure {
            // Never let logging setup crash the app.
            Log.w(TAG, "Persistente Logdatei nicht verfügbar - nur In-Memory-Logging", it)
        }
    }

    fun logFileOrNull(): File? = logFile

    /**
     * Returns all log entries as a formatted plain-text string suitable for sharing.
     */
    fun export(): String = format(buffer.toList())

    /**
     * Returns only the [limit] most recent entries, oldest first.
     */
    fun exportRecent(limit: Int): String = format(buffer.take(limit).reversed())

    // ─── Internal ────────────────────────────────────────────────────────────────

    private fun append(level: LogLevel, tag: String, msg: String, t: Throwable?) {
        // Forward to Android Logcat
        when (level) {
            LogLevel.DEBUG -> if (t != null) Log.d(tag, msg, t) else Log.d(tag, msg)
            LogLevel.INFO  -> if (t != null) Log.i(tag, msg, t) else Log.i(tag, msg)
            LogLevel.WARN  -> if (t != null) Log.w(tag, msg, t) else Log.w(tag, msg)
            LogLevel.ERROR -> if (t != null) Log.e(tag, msg, t) else Log.e(tag, msg)
        }

        val entry = LogEntry(counter.incrementAndGet(), System.currentTimeMillis(), level, tag, msg, t)

        // Prepend so newest entries appear first
        buffer.add(0, entry)
        // Trim to max size
        while (buffer.size > MAX_ENTRIES) buffer.removeAt(buffer.lastIndex)

        _entries.value = buffer.toList()

        logFile?.let { file ->
            fileExecutor.execute {
                runCatching { synchronized(fileLock) { appendToFile(file, entry) } }
                    .onFailure { Log.w(TAG, "Logeintrag konnte nicht geschrieben werden", it) }
            }
        }
    }

    private fun appendToFile(file: File, entry: LogEntry) {
        file.appendText(toLine(entry) + "\n")
        if (file.length() > MAX_FILE_BYTES) trimIfOversized(file)
    }

    /**
     * One entry per line so the file can be parsed back losslessly.
     * Newlines inside message/throwable are escaped, the throwable is appended
     * directly behind the unit-separator - no space, or the message would gain a
     * trailing blank on reload.
     */
    private fun toLine(e: LogEntry): String {
        val ts = SimpleDateFormat(TS_PATTERN, Locale.GERMANY).format(Date(e.timestamp))
        val msg = escape(e.message)
        val thr = e.throwable?.let { escape(it.stackTraceToString()) } ?: ""
        return "$ts [${e.level.label}] ${e.tag}: $msg" + if (thr.isEmpty()) "" else THR_SEP + thr
    }

    private fun escape(raw: String): String = raw
        .replace("\\", "\\\\")
        .replace("\n", "\\n")
        .replace("\r", "")
        .replace(THR_SEP, THR_SEP_ESCAPED)

    private fun unescape(raw: String): String = raw.replace("\\n", "\n").replace("\\\\", "\\")

    private fun loadFromFile(file: File) {
        if (!file.exists() || file.length() == 0L) return
        val lines = runCatching { file.readLines() }.getOrElse { return }
        val parsed = lines.mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            val m = Regex("^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}) \\[([DIWE])\\] ([^:]*): (.*)$")
                .matchEntire(line) ?: return@mapNotNull null
            val (rawTs, rawLevel, tag, rawRest) = m.destructured
            val ts = runCatching {
                SimpleDateFormat(TS_PATTERN, Locale.GERMANY).parse(rawTs)?.time
            }.getOrNull() ?: return@mapNotNull null
            val level = LogLevel.values().firstOrNull { it.label == rawLevel } ?: LogLevel.INFO
            val parts = rawRest.split(THR_SEP, limit = 2)
            LogEntry(
                id = 0L,
                timestamp = ts,
                level = level,
                tag = tag,
                message = unescape(parts[0]),
                throwable = parts.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { parseThrowable(unescape(it)) }
            )
        }
        // File is oldest-first, in-memory buffer is newest-first.
        val ordered = parsed.reversed()
        buffer.addAll(ordered)
        while (buffer.size > MAX_ENTRIES) buffer.removeAt(buffer.lastIndex)
        counter.set(ordered.size.toLong())
        _entries.value = buffer.toList()
    }

    /**
     * The throwable class name and message survive the round trip via a synthetic
     * exception - the UI only ever renders it as text.
     */
    private fun parseThrowable(raw: String): Throwable {
        val firstLine = raw.lineSequence().firstOrNull().orEmpty()
        val cls = firstLine.substringBefore(':', "")
        val msg = firstLine.substringAfter(':', "").trim().ifEmpty { null }
        return when (cls) {
            "java.net.UnknownHostException" -> java.net.UnknownHostException(msg)
            "java.net.SocketTimeoutException" -> java.net.SocketTimeoutException(msg)
            "java.net.ConnectException" -> java.net.ConnectException(msg)
            "java.io.FileNotFoundException" -> java.io.FileNotFoundException(msg)
            else -> RecoveredException(if (msg == null) cls else "$cls: $msg")
        }
    }

    class RecoveredException(message: String) : Exception(message)

    /**
     * Keeps the newest ~70 % of the file so a long-running app cannot fill the disk,
     * while retaining enough history to explain a problem from a previous session.
     */
    private fun trimIfOversized(file: File) {
        if (!file.exists() || file.length() <= MAX_FILE_BYTES) return
        val lines = runCatching { file.readLines() }.getOrElse { return }
        if (lines.isEmpty()) return
        val keep = (lines.size * 0.7).toInt().coerceAtLeast(1)
        val header = "# ${lines.size - keep} aeltere Zeilen entfernt am " +
            SimpleDateFormat(TS_PATTERN, Locale.GERMANY).format(Date())
        runCatching { file.writeText((listOf(header) + lines.takeLast(keep)).joinToString("\n", postfix = "\n")) }
    }

    private fun format(entries: List<LogEntry>): String = buildString {
        appendLine("MyBudgets Fehlerprotokoll")
        appendLine("Exportiert ${SimpleDateFormat(TS_PATTERN, Locale.GERMANY).format(Date())} - ${entries.size} Eintraege")
        appendLine("=".repeat(72))
        val fmt = SimpleDateFormat(TS_PATTERN, Locale.GERMANY)
        entries.forEach { e ->
            append(fmt.format(Date(e.timestamp)))
            append("  [${e.level.label}]")
            append("  ${e.tag}")
            append(":  ")
            appendLine(e.message)
            e.throwable?.let { appendLine(it.stackTraceToString()) }
        }
    }
}
