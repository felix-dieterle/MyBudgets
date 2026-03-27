package de.mybudgets.app.data.repository

import de.mybudgets.app.data.db.TransactionDao
import de.mybudgets.app.data.model.Transaction
import de.mybudgets.app.util.PatternMatcher
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val dao: TransactionDao,
    private val categoryRepository: CategoryRepository,
    private val gamificationRepository: GamificationRepository
) {
    fun observeAll(): Flow<List<Transaction>> = dao.observeAll()
    fun observeByAccount(accountId: Long): Flow<List<Transaction>> = dao.observeByAccount(accountId)
    fun observeByCategory(categoryId: Long): Flow<List<Transaction>> = dao.observeByCategory(categoryId)
    fun observeByDateRange(from: Long, to: Long): Flow<List<Transaction>> = dao.observeByDateRange(from, to)

    suspend fun getRecent(limit: Int = 10): List<Transaction> = dao.getRecent(limit)

    suspend fun getAllRemoteIds(): Set<String> = dao.getAllRemoteIds().toHashSet()

    suspend fun save(transaction: Transaction): Long {
        val categorized = if (transaction.categoryId == null) {
            val matched = PatternMatcher.match(
                transaction.description,
                categoryRepository.getWithPatterns()
            )
            transaction.copy(categoryId = matched)
        } else {
            transaction
        }
        val id = if (categorized.id == 0L) dao.insert(categorized) else {
            dao.update(categorized)
            categorized.id
        }
        gamificationRepository.checkAndAward(dao.count())
        return id
    }

    suspend fun delete(transaction: Transaction) = dao.delete(transaction)
}
