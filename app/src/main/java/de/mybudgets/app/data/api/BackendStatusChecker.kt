package de.mybudgets.app.data.api

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import de.mybudgets.app.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "BackendStatusChecker"
private const val CHECK_INTERVAL_MS = 30_000L

enum class BackendStatus { GRAY, GREEN, RED }

@Singleton
class BackendStatusChecker @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val _status = MutableStateFlow(BackendStatus.GRAY)
    val status: StateFlow<BackendStatus> = _status.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var loopJob: Job? = null
    private var lastUrl = ""
    private var lastKey = ""
    private var lastOffline = true

    private fun prefs(): SharedPreferences =
        context.getSharedPreferences("mybudgets_prefs", Context.MODE_PRIVATE)

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "backend_url" || key == "api_key" || key == "offline_mode") {
            triggerCheck()
        }
    }

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
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

    private suspend fun doCheck() {
        val offline = prefs().getBoolean("offline_mode", true)
        val url = prefs().getString("backend_url", "") ?: ""
        val key = prefs().getString("api_key", "") ?: ""

        lastUrl = url; lastKey = key; lastOffline = offline

        if (offline || url.isBlank()) {
            setStatus(BackendStatus.GRAY, "Sync deaktiviert oder keine URL")
            return
        }

        val reachable = withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(url.trimEnd('/') + "/version")
                    .header("X-API-Key", key)
                    .get()
                    .build()
                httpClient.newCall(request).execute().use { it.isSuccessful }
            }.getOrDefault(false)
        }
        setStatus(if (reachable) BackendStatus.GREEN else BackendStatus.RED, null)
    }

    private fun setStatus(s: BackendStatus, note: String?) {
        if (_status.value == s) return
        _status.value = s
        AppLogger.d(TAG, "Status: $s (url='$lastUrl', offline=$lastOffline)${note?.let { " - $it" } ?: ""}")
    }
}