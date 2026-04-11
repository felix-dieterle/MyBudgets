package de.mybudgets.app.viewmodel

import de.mybudgets.app.data.banking.FintsService
import de.mybudgets.app.data.model.Account
import de.mybudgets.app.data.model.AccountType
import de.mybudgets.app.data.model.Transaction
import de.mybudgets.app.data.model.TransactionType
import de.mybudgets.app.data.repository.AccountRepository
import de.mybudgets.app.data.repository.TransactionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelBankSyncTest {

    private val testDispatcher: TestDispatcher = StandardTestDispatcher()

    private val accountRepo: AccountRepository = mockk(relaxed = true)
    private val txRepo: TransactionRepository = mockk(relaxed = true)
    private val fintsService: FintsService = mockk(relaxed = true)

    private lateinit var viewModel: AccountViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { accountRepo.observeAll() } returns flowOf(emptyList())
        every { accountRepo.observeRealAccounts() } returns flowOf(emptyList())
        every { accountRepo.observeTotalBalance() } returns flowOf(0.0)
        every { txRepo.observeByAccount(any()) } returns flowOf(emptyList())

        viewModel = AccountViewModel(accountRepo, txRepo, fintsService)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun syncBankTransactions_success_importsOnlyNewAndSetsSuccessState() = runTest {
        val account = sampleAccount()
        val existingRemote = "known-remote-id"
        val alreadyKnown = sampleTransaction(account.id, remoteId = existingRemote)
        val newRemote = sampleTransaction(account.id, remoteId = "new-remote-id")
        val withoutRemote = sampleTransaction(account.id, remoteId = null)

        coEvery { accountRepo.getById(account.id) } returns account
        every { fintsService.pinProvider } returns { "1234" }
        coEvery { fintsService.fetchAccountStatement(account, null) } returns Result.success(
            listOf(alreadyKnown, newRemote, withoutRemote)
        )
        coEvery { txRepo.getAllRemoteIds() } returns setOf(existingRemote)
        coEvery { txRepo.save(any()) } returns 1L

        viewModel.syncBankTransactions(account.id, AccountViewModel.NO_FROM_DATE)
        advanceUntilIdle()

        val state = viewModel.bankSyncState.value
        assertTrue(state is BankSyncState.Success)
        assertEquals(2, (state as BankSyncState.Success).importedCount)

        coVerify(exactly = 2) { txRepo.save(any()) }
        coVerify(exactly = 1) {
            txRepo.save(withArg { saved ->
                assertEquals("new-remote-id", saved.remoteId)
                assertEquals(account.id, saved.accountId)
            })
        }
        coVerify(exactly = 1) {
            txRepo.save(withArg { saved ->
                assertEquals(null, saved.remoteId)
                assertEquals(account.id, saved.accountId)
            })
        }
    }

    @Test
    fun syncBankTransactions_whenFetchFails_setsErrorState() = runTest {
        val account = sampleAccount()
        val errorMessage = "FinTS Fehler"

        coEvery { accountRepo.getById(account.id) } returns account
        every { fintsService.pinProvider } returns { "1234" }
        coEvery { fintsService.fetchAccountStatement(account, null) } returns Result.failure(
            IllegalStateException(errorMessage)
        )

        viewModel.syncBankTransactions(account.id, AccountViewModel.NO_FROM_DATE)
        advanceUntilIdle()

        val state = viewModel.bankSyncState.value
        assertTrue(state is BankSyncState.Error)
        assertEquals(errorMessage, (state as BankSyncState.Error).message)
        coVerify(exactly = 0) { txRepo.save(any()) }
    }

    @Test
    fun syncBankTransactions_whenAccountMissing_setsErrorState() = runTest {
        coEvery { accountRepo.getById(999L) } returns null

        viewModel.syncBankTransactions(999L, AccountViewModel.NO_FROM_DATE)
        advanceUntilIdle()

        val state = viewModel.bankSyncState.value
        assertTrue(state is BankSyncState.Error)
        assertEquals("Konto nicht gefunden", (state as BankSyncState.Error).message)
        coVerify(exactly = 0) { txRepo.save(any()) }
    }

    @Test
    fun syncBankTransactions_whenUserIdMissing_setsErrorStateWithoutBankAccess() = runTest {
        val account = sampleAccount(userId = "")

        coEvery { accountRepo.getById(account.id) } returns account
        every { fintsService.pinProvider } returns { "1234" }

        viewModel.syncBankTransactions(account.id, AccountViewModel.NO_FROM_DATE)
        advanceUntilIdle()

        val state = viewModel.bankSyncState.value
        assertTrue(state is BankSyncState.Error)
        assertEquals("Nutzerkennung fehlt", (state as BankSyncState.Error).message)
        coVerify(exactly = 0) { fintsService.fetchAccountStatement(any(), any()) }
        coVerify(exactly = 0) { txRepo.save(any()) }
    }

    @Test
    fun syncBankTransactions_whenPinProviderMissing_setsErrorState() = runTest {
        val account = sampleAccount()

        coEvery { accountRepo.getById(account.id) } returns account
        every { fintsService.pinProvider } returns null

        viewModel.syncBankTransactions(account.id, AccountViewModel.NO_FROM_DATE)
        advanceUntilIdle()

        val state = viewModel.bankSyncState.value
        assertTrue(state is BankSyncState.Error)
        assertEquals("PIN-Dialog nicht verfügbar", (state as BankSyncState.Error).message)
        coVerify(exactly = 0) { txRepo.save(any()) }
    }

    @Test
    fun syncBankTransactions_historicalSync_passesFromDateToFintsService() = runTest {
        val account = sampleAccount()
        val fromDateMillis = 1_710_000_000_000L

        coEvery { accountRepo.getById(account.id) } returns account
        every { fintsService.pinProvider } returns { "1234" }
        coEvery {
            fintsService.fetchAccountStatement(account, Date(fromDateMillis))
        } returns Result.success(emptyList())
        coEvery { txRepo.getAllRemoteIds() } returns emptySet()

        viewModel.syncBankTransactions(account.id, fromDateMillis)
        advanceUntilIdle()

        val state = viewModel.bankSyncState.value
        assertTrue(state is BankSyncState.Success)
        assertEquals(0, (state as BankSyncState.Success).importedCount)
        coVerify(exactly = 1) { fintsService.fetchAccountStatement(account, Date(fromDateMillis)) }
    }

    private fun sampleAccount(id: Long = 1L, userId: String = "user"): Account = Account(
        id = id,
        name = "Testkonto",
        type = AccountType.CHECKING,
        iban = "DE02120300000000202051",
        userId = userId
    )

    private fun sampleTransaction(accountId: Long, remoteId: String?): Transaction = Transaction(
        id = 0L,
        accountId = accountId,
        amount = 10.0,
        description = "Test",
        type = TransactionType.EXPENSE,
        remoteId = remoteId
    )
}
