package de.mybudgets.app

import android.app.Application
import android.os.Looper
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.*
import dagger.hilt.android.HiltAndroidApp
import de.mybudgets.app.data.repository.CategoryRepository
import de.mybudgets.app.data.repository.GamificationRepository
import de.mybudgets.app.data.repository.LabelRepository
import de.mybudgets.app.util.DataSeeder
import de.mybudgets.app.worker.BackendSyncWorker
import de.mybudgets.app.worker.StandingOrderWorker
import de.mybudgets.app.util.AppLogger
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val TAG = "MyBudgetsApp"

@HiltAndroidApp
class MyBudgetsApp : Application(), Configuration.Provider {

    @Inject lateinit var categoryRepository: CategoryRepository
    @Inject lateinit var gamificationRepository: GamificationRepository
    @Inject lateinit var labelRepository: LabelRepository
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Volatile private var globalExceptionHandlerInstalled = false

    private val startupScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, throwable ->
            AppLogger.e(TAG, "Startup-Initialisierung fehlgeschlagen: ${throwable.message}", throwable)
        }
    )

    override val workManagerConfiguration: Configuration
        get() = runCatching {
            Configuration.Builder().apply {
                if (::workerFactory.isInitialized) {
                    setWorkerFactory(workerFactory)
                } else {
                    AppLogger.w(TAG, "WorkManager-WorkerFactory ist beim Startup noch nicht initialisiert. Fallback-Konfiguration wird verwendet.")
                }
            }.build()
        }.getOrElse { e ->
            AppLogger.e(TAG, "WorkManager-Konfiguration fehlgeschlagen. Verwende sichere Fallback-Konfiguration: ${e.message}", e)
            Configuration.Builder().build()
        }

    override fun onCreate() {
        super.onCreate()

        installGlobalExceptionHandler()

        startupScope.launch {
            runCatching {
                if (!categoryRepository.hasDefaultCategories()) {
                    categoryRepository.insertAll(DataSeeder.defaultCategories())
                }
            }.onFailure { e ->
                AppLogger.e(TAG, "Startup: Standard-Kategorien konnten nicht initialisiert werden: ${e.message}", e)
            }

            runCatching {
                if (!gamificationRepository.hasBadges()) {
                    gamificationRepository.seed(DataSeeder.defaultBadges())
                }
            }.onFailure { e ->
                AppLogger.e(TAG, "Startup: Badges konnten nicht initialisiert werden: ${e.message}", e)
            }

            runCatching {
                // Remove any duplicate labels (same name, different ids) that may have
                // accumulated from prior imports or UI interactions.
                labelRepository.deduplicateByName()
            }.onFailure { e ->
                AppLogger.e(TAG, "Startup: Label-Deduplizierung fehlgeschlagen: ${e.message}", e)
            }
        }

        try {
            scheduleBackgroundWorkers()
        } catch (e: Exception) {
            AppLogger.e(TAG, "WorkManager-Planung für Hintergrundaufgaben fehlgeschlagen: ${e.message}", e)
        }
    }

    private fun installGlobalExceptionHandler() {
        if (globalExceptionHandlerInstalled) return
        synchronized(this) {
            if (globalExceptionHandlerInstalled) return
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            if (defaultHandler is SafeLoggingUncaughtExceptionHandler) {
                globalExceptionHandlerInstalled = true
                return
            }
            Thread.setDefaultUncaughtExceptionHandler(SafeLoggingUncaughtExceptionHandler(defaultHandler))
            globalExceptionHandlerInstalled = true
        }
    }

    private class SafeLoggingUncaughtExceptionHandler(
        private val defaultHandler: Thread.UncaughtExceptionHandler?
    ) : Thread.UncaughtExceptionHandler {

        override fun uncaughtException(thread: Thread, throwable: Throwable) {
            runCatching {
                AppLogger.e(TAG, "UNCAUGHT EXCEPTION im Thread '${thread.name}': ${throwable.message}", throwable)
            }.onFailure { loggingError ->
                Log.e(TAG, "Uncaught exception konnte nicht in AppLogger geschrieben werden", loggingError)
            }

            if (shouldDelegateToDefaultUncaughtHandler(thread)) {
                defaultHandler?.uncaughtException(thread, throwable)
            } else {
                AppLogger.w(TAG, "Uncaught Exception im Main-Thread wurde abgefangen, um einen direkten App-Absturz zu vermeiden.")
            }
        }
    }

    private fun scheduleBackgroundWorkers() {
        val workManager = WorkManager.getInstance(this)

        // Backend sync every 6 hours when online
        workManager.enqueueUniquePeriodicWork(
            "backend_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<BackendSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
        )

        // Standing order processing daily
        workManager.enqueueUniquePeriodicWork(
            "standing_order_check",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<StandingOrderWorker>(1, TimeUnit.DAYS)
                .build()
        )
    }
}

internal fun shouldDelegateToDefaultUncaughtHandler(thread: Thread): Boolean {
    return thread != Looper.getMainLooper().thread
}
