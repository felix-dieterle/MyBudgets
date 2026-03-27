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
import de.mybudgets.app.util.AppLogger

private const val TAG = "BankSyncWorker"

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
        val accountId = inputData.getLong(KEY_ACCOUNT_ID, -1L)
        if (accountId < 0) {
            AppLogger.w(TAG, "doWork: keine account_id – Abbruch")
            return Result.failure()
        }

        val account = accountRepo.getById(accountId)
        if (account == null) {
            AppLogger.w(TAG, "doWork: Konto $accountId nicht gefunden")
            return Result.failure()
        }
        if (account.bankCode.isBlank() || account.iban.isBlank()) {
            AppLogger.w(TAG, "doWork: Konto ${account.name} hat keine BLZ/IBAN")
            return Result.failure()
        }
        if (fintsService.pinProvider == null) {
            AppLogger.w(TAG, "doWork: kein PIN-Provider registriert – Abbruch")
            return Result.failure()
        }

        val fromDateMillis = inputData.getLong(KEY_FROM_DATE, NO_FROM_DATE)
        val fromDate = if (fromDateMillis != NO_FROM_DATE) java.util.Date(fromDateMillis) else null

        AppLogger.i(TAG, "doWork: Starte Kontoauszug-Abruf für ${account.iban} ab $fromDate")
        val syncResult = fintsService.fetchAccountStatement(account, fromDate)
        var importedCount = 0
        syncResult.onSuccess { transactions ->
            val existingRemoteIds = transactionRepo.getAllRemoteIds()
            val newTx = transactions.filter { it.remoteId == null || it.remoteId !in existingRemoteIds }
            newTx.forEach { tx -> transactionRepo.save(tx.copy(accountId = account.id)) }
            importedCount = newTx.size
            AppLogger.i(TAG, "doWork: ${newTx.size} neue Buchungen importiert")
            applicationContext.getSharedPreferences("mybudgets_prefs", Context.MODE_PRIVATE)
                .edit().putLong("last_bank_sync_${accountId}", System.currentTimeMillis()).apply()
        }.onFailure { e ->
            AppLogger.e(TAG, "doWork: Kontoauszug-Abruf fehlgeschlagen: ${e.message}", e)
        }

        return if (syncResult.isSuccess)
            Result.success(
                androidx.work.workDataOf(
                    KEY_IMPORTED_COUNT to importedCount,
                    KEY_FROM_DATE to (fromDateMillis.takeIf { it != NO_FROM_DATE } ?: NO_FROM_DATE)
                )
            )
        else Result.failure()
    }

    companion object {
        const val KEY_ACCOUNT_ID    = "account_id"
        /** Epoch-millis for the earliest date to fetch. Use [NO_FROM_DATE] to fetch all. */
        const val KEY_FROM_DATE     = "from_date"
        const val KEY_IMPORTED_COUNT = "imported_count"
        /** Sentinel value meaning "no date filter – fetch complete history". */
        const val NO_FROM_DATE      = -1L
    }
}
