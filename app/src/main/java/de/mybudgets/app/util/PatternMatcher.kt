package de.mybudgets.app.util

import de.mybudgets.app.data.model.Category

object PatternMatcher {
    /**
     * Returns the id of the best matching category for [description],
     * or null if no pattern matches.
     * Level-2 categories are preferred over level-1.
     */
    fun match(description: String, categories: List<Category>): Long? {
        val lower = description.lowercase()
        // Prefer deeper (level-2) matches
        val sorted = categories.sortedByDescending { it.level }
        for (cat in sorted) {
            if (cat.pattern.isBlank()) continue
            try {
                if (Regex(cat.pattern, RegexOption.IGNORE_CASE).containsMatchIn(lower)) {
                    return cat.id
                }
            } catch (_: Exception) {
                // invalid regex in pattern – skip
            }
        }
        return null
    }
}
