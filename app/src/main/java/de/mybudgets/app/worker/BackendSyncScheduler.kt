package de.mybudgets.app.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import de.mybudgets.app.util.AppLogger

private const val TAG = "BackendSyncScheduler"
private const val UNIQUE_WORK_NAME = "backend_sync"

object BackendSyncScheduler {

    /**
     * Plant den Backend-Sync als UniqueWork (KEEP: laeuft bereits einer, wird nicht gestapelt).
     * No-Op wenn offline_mode=true oder keine backend_url gesetzt.
     */
    fun enqueue(context: Context) {
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
        WorkManager.getInstance(context)
            .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
        AppLogger.i(TAG, "Backend-Sync eingeplant")
    }
}