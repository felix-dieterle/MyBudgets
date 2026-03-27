package de.mybudgets.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.mybudgets.app.data.banking.FintsService
import de.mybudgets.app.data.model.Transaction
import de.mybudgets.app.data.model.TransactionType
import de.mybudgets.app.data.repository.AccountRepository
import de.mybudgets.app.data.repository.StandingOrderRepository
import de.mybudgets.app.data.repository.TransactionRepository
import de.mybudgets.app.util.AppLogger

private const val TAG = "StandingOrderWorker"

/**
 * WorkManager worker that checks for due standing orders and executes them via FinTS/HBCI.
 * Scheduled to run daily.
 *
 * For each due standing order that has [sentToBank]=true (i.e. it was registered at the bank),
 * a local transaction is recorded and the nextExecutionDate is advanced by intervalDays.
 *
 * For orders with [sentToBank]=false (local-only), the execution is simulated locally only.
 */
@HiltWorker
class StandingOrderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val standingOrderRepo: StandingOrderRepository,
    private val accountRepo: AccountRepository,
    private val transactionRepo: TransactionRepository,
    private val fintsService: FintsService
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()
        val dueOrders = standingOrderRepo.getDueOrders(now)
        AppLogger.i(TAG, "doWork: ${dueOrders.size} fällige Daueraufträge")

        dueOrders.forEach { order ->
            val account = accountRepo.getById(order.sourceAccountId)
            if (account == null) {
                AppLogger.w(TAG, "Dauerauftrag #${order.id}: Konto ${order.sourceAccountId} nicht gefunden – übersprungen")
                return@forEach
            }

            // Resolve virtual → real account
            val realAccount = if (account.isVirtual && account.parentAccountId != null) {
                accountRepo.getById(account.parentAccountId) ?: account
            } else {
                account
            }

            try {
                // Record local transaction
                transactionRepo.save(
                    Transaction(
                        accountId   = realAccount.id,
                        virtualAccountId = if (account.isVirtual) account.id else null,
                        amount      = order.amount,
                        description = order.purpose.ifBlank { "Dauerauftrag an ${order.recipientName}" },
                        type        = TransactionType.EXPENSE,
                        note        = "Automatisch aus Dauerauftrag #${order.id}"
                    )
                )

                // Advance next execution date
                val nextDate = order.nextExecutionDate + order.intervalDays.toLong() * 24 * 60 * 60 * 1000
                val isStillActive = order.lastExecutionDate == null || nextDate <= order.lastExecutionDate
                standingOrderRepo.save(
                    order.copy(
                        nextExecutionDate = nextDate,
                        isActive = isStillActive
                    )
                )
                AppLogger.i(TAG, "Dauerauftrag #${order.id} an ${order.recipientName}: Buchung gespeichert, nächste Ausführung $nextDate")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Dauerauftrag #${order.id} fehlgeschlagen: ${e.message}", e)
            }
        }

        return Result.success()
    }
}
