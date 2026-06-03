package de.mybudgets.app.util

/**
 * Utility für Category Pattern Matching.
 * 
 * TEXT-Pattern: Alle Keywords (mit | getrennt) müssen in der Beschreibung vorkommen (AND-Logik).
 * Satzzeichen (,.:;/\()[]{}·) werden durch Leerzeichen ersetzt (nicht entfernt!).
 * 
 * Beispiel:
 * - Pattern "EDEKA|Lebensmittel" → TX muss BEIDE Wörter enthalten
 * - TX "Einkauf bei EDEKA Lebensmittel" → ✅ Match
 * - TX "edeka.de einkauf" → normalizeText → "edeka de einkauf" → ✅ enthält "edeka"
 * - TX "EDEKA Abteilung" → ❌ Kein Match (fehlt "Lebensmittel")
 */
object PatternMatcher {
    
    private val PUNCTUATION_REGEX = Regex("[,.:;/\\\\()\\[\\]{}]")
    
    /**
     * Normalisiert einen Text: Ersetzt Satzzeichen durch Leerzeichen, konvertiert zu Lowercase, trimmt.
     * 
     * WICHTIG: Satzzeichen werden durch Leerzeichen ersetzt, nicht entfernt!
     * Grund: "edeka.de|einkauf" darf nicht zu "edekadeeinkauf" werden
     */
    fun normalizeText(text: String): String {
        return text
            .replace(PUNCTUATION_REGEX, " ")  // Satzzeichen → Leerzeichen (nicht entfernen!)
            .trim()
            .lowercase()
    }
    
    /**
     * Extrahiert alle Wörter aus einem Text (getrennt durch Whitespace).
     */
    private fun extractWords(text: String): List<String> {
        return normalizeText(text).split(Regex("\\s+")).filter { it.isNotBlank() }
    }
    
    /**
     * Matched einen TEXT-Pattern gegen Beschreibung + Note (beide kombiniert).
     * Alle Keywords müssen enthalten sein (AND-Logik).
     * 
     * @param patternValue Pipe-getrennte Keywords: "EDEKA|Lebensmittel"
     * @param description TX Beschreibung
     * @param note TX Note (z.B. IBAN oder Empfänger)
     * @return true wenn ALLE Keywords vorhanden
     */
    fun matchTextPattern(
        patternValue: String,
        description: String,
        note: String
    ): Boolean {
        val keywords = patternValue.split("|").map { normalizeText(it) }.filter { it.isNotBlank() }
        if (keywords.isEmpty()) return false
        
        val combinedText = normalizeText("$description $note")
        val words = extractWords(combinedText)
        
        // ALLE Keywords müssen vorhanden sein (AND-Logik)
        return keywords.all { keyword ->
            words.any { word ->
                // Exaktes Match oder als Substring eines Wortes
                word == keyword || word.contains(keyword)
            }
        }
    }
    
    /**
     * Matched einen IBAN-Pattern gegen die Note.
     */
    fun matchIbanPattern(
        patternValue: String,
        note: String
    ): Boolean {
        return normalizeText(note).contains(normalizeText(patternValue))
    }
    
    /**
     * Generischer Matcher - delegiert zu spezialisierten Funktionen.
     */
    fun matches(
        patternType: String,
        patternValue: String,
        description: String,
        note: String
    ): Boolean {
        return when (patternType) {
            "TEXT" -> matchTextPattern(patternValue, description, note)
            "IBAN" -> matchIbanPattern(patternValue, note)
            else -> false
        }
    }
}
