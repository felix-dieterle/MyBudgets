package de.mybudgets.app.data.db

import androidx.room.*
import de.mybudgets.app.data.model.GamificationBadge
import kotlinx.coroutines.flow.Flow

@Dao
interface GamificationDao {
    @Query("SELECT * FROM gamification_badges ORDER BY type")
    fun observeAll(): Flow<List<GamificationBadge>>

    @Query("SELECT * FROM gamification_badges WHERE earnedAt IS NOT NULL ORDER BY earnedAt DESC")
    fun observeEarned(): Flow<List<GamificationBadge>>

    @Query("SELECT * FROM gamification_badges WHERE type = :type LIMIT 1")
    suspend fun getByType(type: String): GamificationBadge?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(badges: List<GamificationBadge>)

    @Update
    suspend fun update(badge: GamificationBadge)
}
