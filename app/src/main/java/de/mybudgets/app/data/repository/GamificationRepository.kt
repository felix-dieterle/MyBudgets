package de.mybudgets.app.data.repository

import de.mybudgets.app.data.db.GamificationDao
import de.mybudgets.app.data.model.BadgeType
import de.mybudgets.app.data.model.GamificationBadge
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GamificationRepository @Inject constructor(
    private val dao: GamificationDao
) {
    fun observeAll(): Flow<List<GamificationBadge>> = dao.observeAll()
    fun observeEarned(): Flow<List<GamificationBadge>> = dao.observeEarned()

    suspend fun seed(badges: List<GamificationBadge>) = dao.insertAll(badges)
    suspend fun hasBadges(): Boolean = dao.count() > 0

    suspend fun checkAndAward(transactionCount: Int) {
        when (transactionCount) {
            1    -> award(BadgeType.FIRST_TRANSACTION)
            10   -> award(BadgeType.TRANSACTIONS_10)
            100  -> award(BadgeType.TRANSACTIONS_100)
        }
    }

    private suspend fun award(type: BadgeType) {
        val badge = dao.getByType(type.name) ?: return
        if (badge.earnedAt == null) {
            dao.update(badge.copy(earnedAt = System.currentTimeMillis()))
        }
    }
}
