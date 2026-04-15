package de.mybudgets.app.data.db

import androidx.room.*
import de.mybudgets.app.data.model.Account
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY name")
    fun observeAll(): Flow<List<Account>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: Long): Account?

    @Query("SELECT * FROM accounts WHERE isVirtual = 0 ORDER BY name")
    fun observeRealAccounts(): Flow<List<Account>>

    @Query("SELECT * FROM accounts WHERE parentAccountId = :parentId")
    fun observeVirtualAccounts(parentId: Long): Flow<List<Account>>

    @Query("SELECT * FROM accounts WHERE isVirtual = 1 AND autoAssignPattern != ''")
    suspend fun getVirtualAccountsWithPatterns(): List<Account>

    @Query("SELECT SUM(balance) FROM accounts WHERE isVirtual = 0")
    fun observeTotalBalance(): Flow<Double?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: Account): Long

    @Update
    suspend fun update(account: Account)

    @Delete
    suspend fun delete(account: Account)
}
