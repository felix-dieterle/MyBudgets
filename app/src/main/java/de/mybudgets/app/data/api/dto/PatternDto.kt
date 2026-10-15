package de.mybudgets.app.data.api.dto

/**
 * Antwort von `GET /patterns` (finn, US-004).
 *
 * Feldnamen entsprechen bewusst den Servernamen; die Gson-Policy
 * LOWER_CASE_WITH_UNDERSCORES (ApiClient.apiGson) mappt sie automatisch
 * (musterKey -> muster_key, matcherVersion -> matcher_version, ...).
 */
data class PatternsResponse(
    val matcherVersion: String = "",
    val patterns: List<PatternDto> = emptyList()
)

data class PatternDto(
    val musterKey: String = "",
    val matcherVersion: String = "",
    val wichtigkeit: Int = 0,
    val kuerzbarkeit: Int = 0,
    /** Kategorie-Pfad aus dem Dashboard, z. B. "Versicherungen > KFZ" (oder null). */
    val kategorieOverride: String? = null,
    val notiz: String = "",
    val updatedAt: Long = 0
)
