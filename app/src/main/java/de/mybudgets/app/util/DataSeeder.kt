package de.mybudgets.app.util

import de.mybudgets.app.data.model.BadgeType
import de.mybudgets.app.data.model.Category
import de.mybudgets.app.data.model.GamificationBadge

object DataSeeder {

    // ── Default 2-level categories ────────────────────────────────────────────
    fun defaultCategories(): List<Category> = listOf(
        // Level 1
        Category(name = "Wohnen",       color = 0xFF5C6BC0.toInt(), icon = "ic_home",      pattern = "miete|strom|wasser|gas|internet|versicherung", level = 1, isDefault = true),
        Category(name = "Lebensmittel", color = 0xFF66BB6A.toInt(), icon = "ic_food",       pattern = "rewe|edeka|aldi|lidl|kaufland|netto|supermarkt|bäcker", level = 1, isDefault = true),
        Category(name = "Transport",    color = 0xFF42A5F5.toInt(), icon = "ic_transport",  pattern = "tankstelle|bahn|db|ubahn|bus|öpnv", level = 1, isDefault = true),
        Category(name = "Freizeit",     color = 0xFFFF7043.toInt(), icon = "ic_leisure",    pattern = "kino|restaurant|café|sport|fitness|verein", level = 1, isDefault = true),
        Category(name = "Gesundheit",   color = 0xFF26C6DA.toInt(), icon = "ic_health",     pattern = "apotheke|arzt|krankenhaus|medikament", level = 1, isDefault = true),
        Category(name = "Einkommen",    color = 0xFF4CAF50.toInt(), icon = "ic_income",     pattern = "gehalt|lohn|rente|zinsen|dividende", level = 1, isDefault = true),
        Category(name = "Sparen",       color = 0xFF8D6E63.toInt(), icon = "ic_savings",    pattern = "sparkasse|sparbuch|depot|etf|fonds", level = 1, isDefault = true),
        Category(name = "Bekleidung",   color = 0xFFAB47BC.toInt(), icon = "ic_clothing",   pattern = "zara|h&m|primark|c&a", level = 1, isDefault = true),
        // Level 2 – Wohnen (parentCategoryId set after insert)
        Category(name = "Miete",            color = 0xFF5C6BC0.toInt(), icon = "ic_home",     pattern = "miete|kaltmiete|warmmiete",               level = 2, isDefault = true),
        Category(name = "Strom & Gas",      color = 0xFF5C6BC0.toInt(), icon = "ic_energy",   pattern = "strom|gas|stadtwerke|energie",             level = 2, isDefault = true),
        Category(name = "Internet",         color = 0xFF5C6BC0.toInt(), icon = "ic_wifi",     pattern = "internet|telekom|vodafone|o2|dsl",         level = 2, isDefault = true),
        Category(name = "Versicherungen",   color = 0xFF5C6BC0.toInt(), icon = "ic_shield",   pattern = "versicherung|haftpflicht|hausrat",         level = 2, isDefault = true),
        // Level 2 – Lebensmittel
        Category(name = "Supermarkt",  color = 0xFF66BB6A.toInt(), icon = "ic_shopping_cart", pattern = "rewe|edeka|aldi|lidl|kaufland|netto",     level = 2, isDefault = true),
        Category(name = "Restaurant",  color = 0xFF66BB6A.toInt(), icon = "ic_restaurant",    pattern = "restaurant|imbiss|döner|pizza|sushi",     level = 2, isDefault = true),
        Category(name = "Lieferdienst",color = 0xFF66BB6A.toInt(), icon = "ic_delivery",      pattern = "lieferando|deliveroo|uber eats",          level = 2, isDefault = true),
        // Level 2 – Transport
        Category(name = "ÖPNV",       color = 0xFF42A5F5.toInt(), icon = "ic_tram",  pattern = "bahn|mvg|hvv|rnv|kvb|öpnv|ticket",  level = 2, isDefault = true),
        Category(name = "Tankstelle", color = 0xFF42A5F5.toInt(), icon = "ic_fuel",  pattern = "tankstelle|aral|shell|esso|bp|total", level = 2, isDefault = true),
        Category(name = "Parken",     color = 0xFF42A5F5.toInt(), icon = "ic_park",  pattern = "parkhaus|parkplatz|parking",         level = 2, isDefault = true),
        // Level 2 – Gesundheit
        Category(name = "Apotheke", color = 0xFF26C6DA.toInt(), icon = "ic_pharmacy", pattern = "apotheke|medikament", level = 2, isDefault = true),
        Category(name = "Arzt",     color = 0xFF26C6DA.toInt(), icon = "ic_doctor",   pattern = "arzt|zahnarzt|praxis|klinik", level = 2, isDefault = true),
        // Level 2 – Einkommen
        Category(name = "Gehalt", color = 0xFF4CAF50.toInt(), icon = "ic_work",     pattern = "gehalt|lohn|arbeitgeber", level = 2, isDefault = true),
        Category(name = "Zinsen", color = 0xFF4CAF50.toInt(), icon = "ic_trending", pattern = "zinsen|dividende|ertrag", level = 2, isDefault = true)
    )

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
