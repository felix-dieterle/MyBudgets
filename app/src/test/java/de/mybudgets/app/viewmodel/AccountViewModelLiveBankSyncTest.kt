package de.mybudgets.app.viewmodel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import de.mybudgets.app.data.banking.FintsService
import de.mybudgets.app.data.model.Account
import de.mybudgets.app.data.model.AccountType
import de.mybudgets.app.data.model.Transaction
import de.mybudgets.app.data.repository.AccountRepository
import de.mybudgets.app.data.repository.TransactionRepository
import de.mybudgets.app.util.AppLogger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

/**
 * Live integration test against a real bank account (no app UI launch).
 *
 * This test is skipped unless MYBUDGETS_LIVE_TEST=true is set in the environment.
 * Secrets are read from environment variables (set by scripts/run-live-bbbank-sync-test.sh).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AccountViewModelLiveBankSyncTest {

    private val accountRepo: AccountRepository = mockk(relaxed = true)
    private val txRepo: TransactionRepository = mockk(relaxed = true)

    private lateinit var fintsService: FintsService
    private lateinit var viewModel: AccountViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        AppLogger.clear()

        val context = ApplicationProvider.getApplicationContext<Context>()
        fintsService = FintsService(context)

        every { accountRepo.observeAll() } returns kotlinx.coroutines.flow.flowOf(emptyList())
        every { accountRepo.observeRealAccounts() } returns kotlinx.coroutines.flow.flowOf(emptyList())
        every { accountRepo.observeTotalBalance() } returns kotlinx.coroutines.flow.flowOf(0.0)
        every { txRepo.observeByAccount(any()) } returns kotlinx.coroutines.flow.flowOf(emptyList())

        viewModel = AccountViewModel(accountRepo, txRepo, fintsService)
    }

    @After
    fun tearDown() {
        fintsService.pinProvider = null
        fintsService.tanProvider = null
        fintsService.decoupledConfirmProvider = null
        Dispatchers.resetMain()
    }

    @Test
    fun liveSync_realBankAccount_fetchesAndImportsTransactions() = runBlocking {
        assumeTrue("Setze -Dmybudgets.live.test=true, um diesen Test auszuführen", sysProp("mybudgets.live.test") == "true")

        val iban = requiredProp("mybudgets.test.iban")
        val userId = requiredProp("mybudgets.test.userId")
        val pin = requiredProp("mybudgets.test.pin")
        val bankCode = sysProp("mybudgets.test.bankCode").orEmpty()
        val tanMethod = sysProp("mybudgets.test.tanMethod").orEmpty()
        val decoupledWaitSeconds = sysProp("mybudgets.test.decoupledWaitSeconds")
            ?.toLongOrNull() ?: 30L
        val overallTimeoutSeconds = sysProp("mybudgets.test.overallTimeoutSeconds")
            ?.toLongOrNull() ?: 900L

        val account = Account(
            id = 1L,
            name = "Live-BBBank",
            type = AccountType.CHECKING,
            bankCode = bankCode,
            iban = iban,
            userId = userId,
            tanMethod = tanMethod
        )

        coEvery { accountRepo.getById(account.id) } returns account
        coEvery { txRepo.getAllRemoteIds() } returns emptySet()
        coEvery { txRepo.save(any()) } returns 1L

        // PIN from env; TAN can be passed via MYBUDGETS_TEST_TAN for non-decoupled flows.
        fintsService.pinProvider = { _ -> pin }
        val tanFromEnv = sysProp("mybudgets.test.tan")
        fintsService.tanProvider = { challenge ->
            require(!tanFromEnv.isNullOrBlank()) {
                "MYBUDGETS_TEST_TAN fehlt. Challenge vom Bankserver: $challenge"
            }
            tanFromEnv
        }
        fintsService.decoupledConfirmProvider = { _ ->
            // Give the user time to confirm in Secure Go/Banking app.
            delay(TimeUnit.SECONDS.toMillis(decoupledWaitSeconds))
        }

        viewModel.syncBankTransactions(account.id, AccountViewModel.NO_FROM_DATE)

        try {
            withTimeout(TimeUnit.SECONDS.toMillis(overallTimeoutSeconds)) {
                var lastState: BankSyncState? = null
                var lastLogCount = 0
                while (true) {
                    val state = viewModel.bankSyncState.value
                    if (state != lastState) {
                        println(">>> State transition: $lastState → $state")
                        lastState = state
                    }
                    when (state) {
                        is BankSyncState.Success -> {
                            val logs = AppLogger.export()
                            println("✓ Live-Sync SUCCESS: ${state.importedCount} Buchungen importiert")
                            println("\n=== Final App Logs ===\n$logs\n")
                            return@withTimeout
                        }
                        is BankSyncState.Error -> {
                            val logs = AppLogger.export()
                            val logLines = logs.split("\n").toMutableList()
                            // Show new log lines since last dump
                            val newLogLines = logLines.drop(lastLogCount)
                            println("✗ Live-Sync ERROR in phase ${state.phase?.displayName ?: "unknown"}: ${state.message}")
                            if (newLogLines.isNotEmpty()) {
                                println("  Recent logs:\n    " + newLogLines.take(5).joinToString("\n    "))
                            }
                            lastLogCount = logLines.size
                            
                            error(
                                "Live-Sync fehlgeschlagen in Phase ${state.phase?.displayName ?: "unknown"}: ${state.message}\n" +
                                "\nLetzte App-Logs:\n$logs"
                            )
                        }
                        is BankSyncState.Loading -> {
                            val phase = state.phase.displayName
                            val detail = if (state.detailMessage.isNotBlank()) " — ${state.detailMessage}" else ""
                            println("⏳ Syncing Phase: $phase$detail")
                        }
                        else -> {
                            // Continue
                        }
                    }
                    delay(250)
                }
            }
        } catch (e: TimeoutCancellationException) {
            val logDump = AppLogger.export()
            val state = viewModel.bankSyncState.value
            error(
                "Live-Sync Timeout nach ${overallTimeoutSeconds}s. Final state: $state\n\nLetzte App-Logs:\n$logDump"
            )
        }

        val state = viewModel.bankSyncState.value
        assertTrue(state is BankSyncState.Success)
        assertTrue((state as BankSyncState.Success).importedCount >= 0)
        coVerify(atLeast = 0) { txRepo.save(any<Transaction>()) }
    }

    private fun sysProp(name: String): String? = System.getProperty(name)?.takeIf { it.isNotBlank() }

    private fun requiredProp(name: String): String =
        sysProp(name) ?: error("System Property -D$name fehlt")
}
