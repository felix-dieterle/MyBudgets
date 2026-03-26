package de.mybudgets.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.mybudgets.app.data.banking.FintsService
import de.mybudgets.app.data.repository.AccountRepository
import de.mybudgets.app.data.repository.TransactionRepository

/**
 * WorkManager worker that fetches account statements from the bank via FinTS/HBCI
 * and stores new transactions locally.
 *
 * Note: This worker requires a PIN/TAN interaction and therefore cannot run fully
 * unattended in the background. It is triggered manually from the AccountDetailFragment.
 * PIN/TAN providers must be set on [FintsService] before enqueuing this worker,
 * or this worker will return Result.retry() if no providers are available.
 */
@HiltWorker
class BankSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val accountRepo: AccountRepository,
    private val transactionRepo: TransactionRepository,
    private val fintsService: FintsService
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val accountId = inputData.getLong("account_id", -1L)
        if (accountId < 0) return Result.failure()

        val account = accountRepo.getById(accountId) ?: return Result.failure()
        if (account.bankCode.isBlank() || account.iban.isBlank()) return Result.failure()
        if (fintsService.pinProvider == null) return Result.failure()

        val syncResult = fintsService.fetchAccountStatement(account)
        syncResult.onSuccess { transactions ->
            val existingRemoteIds = transactionRepo.getRecent(500).mapNotNull { it.remoteId }.toHashSet()
            transactions.filter { it.remoteId !in existingRemoteIds }.forEach { tx ->
                transactionRepo.save(tx.copy(accountId = account.id))
            }
            applicationContext.getSharedPreferences("mybudgets_prefs", Context.MODE_PRIVATE)
                .edit().putLong("last_bank_sync_${accountId}", System.currentTimeMillis()).apply()
        }

        return if (syncResult.isSuccess) Result.success() else Result.failure()
    }
}
