package de.mybudgets.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class BadgeType {
    FIRST_TRANSACTION, STREAK_7_DAYS, STREAK_30_DAYS,
    TRANSACTIONS_10, TRANSACTIONS_100,
    BUDGET_GOAL_MET, SAVING_STREAK,
    FIRST_ACCOUNT, CATEGORIES_SET, FIRST_EXPORT
}

@Entity(tableName = "gamification_badges")
data class GamificationBadge(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val iconRes: String = "ic_badge",
    val earnedAt: Long? = null,
    val type: BadgeType
)
