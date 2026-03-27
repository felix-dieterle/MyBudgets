package de.mybudgets.app.util

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
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
 * In-app logger that writes to both Android Logcat and an in-memory ring buffer.
 * Observe [entries] to display a live log console in the UI.
 * Use [export] to get all entries as a plain-text string (e.g. to share via Intent).
 */
object AppLogger {

    private const val MAX_ENTRIES = 500

    private val counter = AtomicLong(0)
    private val buffer = CopyOnWriteArrayList<LogEntry>()

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    // ─── Public logging API ──────────────────────────────────────────────────────

    fun d(tag: String, msg: String, t: Throwable? = null) = append(LogLevel.DEBUG, tag, msg, t)
    fun i(tag: String, msg: String, t: Throwable? = null) = append(LogLevel.INFO,  tag, msg, t)
    fun w(tag: String, msg: String, t: Throwable? = null) = append(LogLevel.WARN,  tag, msg, t)
    fun e(tag: String, msg: String, t: Throwable? = null) = append(LogLevel.ERROR, tag, msg, t)

    fun clear() {
        buffer.clear()
        _entries.value = emptyList()
    }

    /**
     * Returns all log entries as a formatted plain-text string suitable for sharing.
     */
    fun export(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.GERMANY)
        return buildString {
            appendLine("MyBudgets Fehlerprotokoll  –  exportiert ${fmt.format(Date())}")
            appendLine("=".repeat(72))
            buffer.forEach { e ->
                append(fmt.format(Date(e.timestamp)))
                append("  [${e.level.label}]")
                append("  ${e.tag}")
                append(":  ")
                appendLine(e.message)
                e.throwable?.let { appendLine(it.stackTraceToString()) }
            }
        }
    }

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
    }
}
