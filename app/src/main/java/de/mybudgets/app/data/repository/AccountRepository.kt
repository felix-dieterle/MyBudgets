package de.mybudgets.app.data.repository

import de.mybudgets.app.data.db.AccountDao
import de.mybudgets.app.data.model.Account
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository @Inject constructor(
    private val dao: AccountDao
) {
    fun observeAll(): Flow<List<Account>> = dao.observeAll()
    fun observeRealAccounts(): Flow<List<Account>> = dao.observeRealAccounts()
    fun observeVirtualAccounts(parentId: Long): Flow<List<Account>> = dao.observeVirtualAccounts(parentId)
    fun observeTotalBalance(): Flow<Double?> = dao.observeTotalBalance()

    suspend fun getById(id: Long): Account? = dao.getById(id)
    suspend fun save(account: Account): Long = if (account.id == 0L) dao.insert(account) else { dao.update(account); account.id }
    suspend fun delete(account: Account) = dao.delete(account)
}
