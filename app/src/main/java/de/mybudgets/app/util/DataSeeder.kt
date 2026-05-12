package de.mybudgets.app.util

import de.mybudgets.app.data.db.DefaultCategories
import de.mybudgets.app.data.model.BadgeType
import de.mybudgets.app.data.model.Category
import de.mybudgets.app.data.model.GamificationBadge

object DataSeeder {

    fun defaultCategories(): List<Category> = DefaultCategories.getDefaultCategories()

    // ── Gamification badges ──────────────────────────────────────────────────
    fun defaultBadges(): List<GamificationBadge> = listOf(
        GamificationBadge(name = "Erste Transaktion",    description = "Erste Buchung erfasst!",                            type = BadgeType.FIRST_TRANSACTION),
        GamificationBadge(name = "Erstes Konto",         description = "Erstes Konto angelegt.",                            type = BadgeType.FIRST_ACCOUNT),
        GamificationBadge(name = "10 Transaktionen",     description = "10 Buchungen erfasst.",                             type = BadgeType.TRANSACTIONS_10),
        GamificationBadge(name = "100 Transaktionen",    description = "100 Buchungen – du bist ein Profi!",                type = BadgeType.TRANSACTIONS_100),
        GamificationBadge(name = "7 Tage Streak",        description = "7 Tage in Folge Buchungen erfasst.",                type = BadgeType.STREAK_7_DAYS),
        GamificationBadge(name = "30 Tage Streak",       description = "30 Tage in Folge Buchungen erfasst. Fantastisch!", type = BadgeType.STREAK_30_DAYS),
        GamificationBadge(name = "Sparziel erreicht",    description = "Ein Sparziel wurde erreicht!",                     type = BadgeType.BUDGET_GOAL_MET),
        GamificationBadge(name = "Spar-Streak",          description = "3 Monate in Folge gespart.",                       type = BadgeType.SAVING_STREAK),
        GamificationBadge(name = "Kategorien gesetzt",   description = "Alle Transaktionen kategorisiert.",                 type = BadgeType.CATEGORIES_SET),
        GamificationBadge(name = "Erster Export",        description = "Daten als JSON exportiert.",                        type = BadgeType.FIRST_EXPORT)
    )
}
