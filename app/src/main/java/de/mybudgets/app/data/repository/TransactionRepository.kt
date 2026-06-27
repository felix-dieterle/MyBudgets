package de.mybudgets.app.data.repository

import de.mybudgets.app.data.db.TransactionDao
import de.mybudgets.app.data.model.CategoryPattern
import de.mybudgets.app.data.model.Transaction
import de.mybudgets.app.data.model.TransactionType
import de.mybudgets.app.data.model.TransactionWithCategory
import de.mybudgets.app.util.TransactionAiHelper
import de.mybudgets.app.util.VirtualAccountMatcher
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val dao: TransactionDao,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val gamificationRepository: GamificationRepository,
    private val categoryPatternRepository: CategoryPatternRepository
) {
    fun observeAll(): Flow<List<Transaction>> = dao.observeAll()
    fun observeAllWithCategory(): Flow<List<TransactionWithCategory>> = dao.observeAllWithCategory()
    fun observeByAccount(accountId: Long): Flow<List<Transaction>> = dao.observeByAccount(accountId)
    fun observeByCategory(categoryId: Long): Flow<List<Transaction>> = dao.observeByCategory(categoryId)
    fun observeByDateRange(from: Long, to: Long): Flow<List<Transaction>> = dao.observeByDateRange(from, to)

    suspend fun getRecent(limit: Int = 10): List<Transaction> = dao.getRecent(limit)

    suspend fun getAllRemoteIds(): Set<String> = dao.getAllRemoteIds().toHashSet()

    suspend fun getLatestDateForAccount(accountId: Long): Long? = dao.getLatestDateForAccount(accountId)
    suspend fun getEarliestDateForAccount(accountId: Long): Long? = dao.getEarliestDateForAccount(accountId)
    suspend fun getTransactionCountForAccount(accountId: Long): Int = dao.getTransactionCountForAccount(accountId)

    suspend fun markTransactionsAsRecurring(ids: List<Long>, intervalDays: Int) {
        dao.markAsRecurring(ids, intervalDays)
    }

    suspend fun suggestCategoryId(
        description: String,
        amount: Double,
        type: TransactionType
    ): Long? {
        val recent = dao.getRecent(500)
        val categoriesWithPatterns = categoryRepository.getWithPatterns()
        return TransactionAiHelper.suggestCategoryId(
            description = description,
            amount = amount,
            type = type,
            recentTransactions = recent,
            patternCategories = categoriesWithPatterns
        )
    }

    private var patternMatchedId: Long? = null

    suspend fun save(transaction: Transaction): Long {
        patternMatchedId = null
        val recent = dao.getRecent(500)
        val categoriesWithPatterns = categoryRepository.getWithPatterns()

        val withVirtualMapping = if (transaction.virtualAccountId == null && transaction.remoteId != null) {
            val virtual = VirtualAccountMatcher.match(
                transaction.description,
                accountRepository.getVirtualAccountsWithPatterns()
            )
            if (virtual != null) {
                transaction.copy(
                    accountId = virtual.parentAccountId ?: transaction.accountId,
                    virtualAccountId = virtual.id
                )
            } else transaction
        } else transaction

        val patternMatched = if (withVirtualMapping.categoryId == null && withVirtualMapping.description.isNotBlank()) {
            categoryPatternRepository.findTextMatch(withVirtualMapping.description, withVirtualMapping.note)?.let { pattern ->
                patternMatchedId = pattern.id
                val matchedName = pattern.matchedName.takeIf { it.isNotBlank() }
                withVirtualMapping.copy(
                    categoryId = pattern.categoryId,
                    description = matchedName ?: withVirtualMapping.description
                )
            } ?: withVirtualMapping
        } else {
            withVirtualMapping
        }

        val categorized = if (patternMatched.categoryId == null) {
            val matched = TransactionAiHelper.suggestCategoryId(
                description = patternMatched.description,
                amount = patternMatched.amount,
                type = patternMatched.type,
                recentTransactions = recent,
                patternCategories = categoriesWithPatterns
            )
            patternMatched.copy(categoryId = matched)
        } else {
            patternMatched
        }

        val recurring = if (!categorized.isRecurring && categorized.recurringIntervalDays <= 0) {
            val inferredInterval = TransactionAiHelper.inferRecurringIntervalDays(
                description = categorized.description,
                amount = categorized.amount,
                type = categorized.type,
                currentDate = categorized.date,
                recentTransactions = recent
            )
            if (inferredInterval != null) {
                categorized.copy(isRecurring = true, recurringIntervalDays = inferredInterval)
            } else categorized
        } else categorized

        val id = if (recurring.id == 0L) dao.insert(recurring) else {
            dao.update(recurring)
            recurring.id
        }
        patternMatchedId?.let { categoryPatternRepository.incrementUsage(it) }
        gamificationRepository.checkAndAward(dao.count())
        return id
    }

    suspend fun delete(transaction: Transaction) = dao.delete(transaction)
}
