package de.mybudgets.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.*
import dagger.hilt.android.HiltAndroidApp
import de.mybudgets.app.data.repository.CategoryRepository
import de.mybudgets.app.data.repository.GamificationRepository
import de.mybudgets.app.util.DataSeeder
import de.mybudgets.app.worker.BackendSyncWorker
import de.mybudgets.app.worker.StandingOrderWorker
import de.mybudgets.app.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val TAG = "MyBudgetsApp"

@HiltAndroidApp
class MyBudgetsApp : Application(), Configuration.Provider {

    @Inject lateinit var categoryRepository: CategoryRepository
    @Inject lateinit var gamificationRepository: GamificationRepository
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        installGlobalExceptionHandler()

        CoroutineScope(Dispatchers.IO).launch {
            categoryRepository.insertAll(DataSeeder.defaultCategories())
            gamificationRepository.seed(DataSeeder.defaultBadges())
        }

        scheduleBackgroundWorkers()
    }

    /**
     * Installs a global uncaught-exception handler that logs the crash to [AppLogger]
     * before delegating to the default handler (which terminates the process).
     */
    private fun installGlobalExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            AppLogger.e(TAG, "UNCAUGHT EXCEPTION im Thread '${thread.name}': ${throwable.message}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
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

