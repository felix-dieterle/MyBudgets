package de.mybudgets.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.mybudgets.app.data.api.ApiClient
import de.mybudgets.app.data.api.dto.TransactionDto
import de.mybudgets.app.data.model.Transaction
import de.mybudgets.app.data.model.TransactionType
import de.mybudgets.app.data.repository.AccountRepository
import de.mybudgets.app.data.repository.TransactionRepository
import de.mybudgets.app.util.AppLogger

private const val TAG = "BackendSyncWorker"

@HiltWorker
class BackendSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val accountRepo: AccountRepository,
    private val transactionRepo: TransactionRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("mybudgets_prefs", Context.MODE_PRIVATE)
        val backendUrl = prefs.getString("backend_url", "") ?: ""
        val apiKey     = prefs.getString("api_key", "") ?: ""
        val syncEnabled = !prefs.getBoolean("offline_mode", true)

        if (!syncEnabled || backendUrl.isBlank()) return Result.success()

        return try {
            AppLogger.i(TAG, "doWork: Starte Backend-Sync gegen $backendUrl")
            val api = ApiClient.getService(backendUrl, apiKey)

            // Push local transactions without remoteId
            val allLocal = transactionRepo.getRecent(500)
            val unpushed = allLocal.filter { it.remoteId == null }
            AppLogger.d(TAG, "doWork: ${unpushed.size} lokale Buchungen ohne remoteId werden hochgeladen")
            unpushed.forEach { tx ->
                val dto = tx.toDto()
                val resp = api.createTransaction(dto)
                if (resp.isSuccessful) {
                    val remoteId = resp.body()?.get("id")?.toString()
                    if (remoteId != null) {
                        transactionRepo.save(tx.copy(remoteId = remoteId))
                    }
                } else {
                    AppLogger.w(TAG, "doWork: Upload fehlgeschlagen für txId=${tx.id}: HTTP ${resp.code()}")
                }
            }

            // Pull remote transactions – deduplicate against known remoteIds
            val existingRemoteIds = allLocal.mapNotNull { it.remoteId }.toHashSet()
            val serverResp = api.getTransactions(limit = 200)
            if (serverResp.isSuccessful) {
                val pulled = serverResp.body()?.filter { dto -> dto.id.toString() !in existingRemoteIds } ?: emptyList()
                pulled.forEach { dto -> transactionRepo.save(dto.toTransaction()) }
                AppLogger.i(TAG, "doWork: ${pulled.size} neue Buchungen vom Server importiert")
            } else {
                AppLogger.w(TAG, "doWork: Server-Abruf fehlgeschlagen: HTTP ${serverResp.code()}")
            }

            prefs.edit().putLong("last_sync_time", System.currentTimeMillis()).apply()
            Result.success()
        } catch (e: Exception) {
            AppLogger.e(TAG, "doWork: Backend-Sync fehlgeschlagen: ${e.message}", e)
            Result.retry()
        }
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
