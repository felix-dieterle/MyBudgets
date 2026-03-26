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
            val api = ApiClient.getService(backendUrl, apiKey)

            // Push local transactions
            val recent = transactionRepo.getRecent(100)
            recent.filter { it.remoteId == null }.forEach { tx ->
                val dto = tx.toDto()
                val resp = api.createTransaction(dto)
                if (resp.isSuccessful) {
                    val remoteId = resp.body()?.get("id")?.toString()
                    if (remoteId != null) {
                        transactionRepo.save(tx.copy(remoteId = remoteId))
                    }
                }
            }

            // Pull remote transactions
            val serverResp = api.getTransactions(limit = 200)
            if (serverResp.isSuccessful) {
                serverResp.body()?.forEach { dto ->
                    val existing = transactionRepo.getRecent(1000).firstOrNull { it.remoteId == dto.id.toString() }
                    if (existing == null) {
                        transactionRepo.save(dto.toTransaction())
                    }
                }
            }

            prefs.edit().putLong("last_sync_time", System.currentTimeMillis()).apply()
            Result.success()
        } catch (e: Exception) {
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
