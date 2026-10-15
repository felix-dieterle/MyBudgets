package de.mybudgets.app.util

import java.util.Locale

/**
 * Port von finn `insights_normalisieren()` (public/apps/finn/insights/insights.php).
 *
 * Das Dashboard gruppiert wiederkehrende Buchungen ueber diese Normalisierung und
 * speichert Muster-Bewertungen unter `pattern_evaluations.muster_key`. Nur wenn die
 * App dieselbe Normalisierung anwendet, treffen die im Dashboard gesetzten
 * `kategorie_override`-Werte die richtigen Buchungen (US-004).
 *
 * Die Reihenfolge ist bewusst identisch zum Original: Bankreferenzen abschneiden,
 * "uhr" entfernen, Ziffern entfernen, Rauschwoerter entfernen, Satzzeichen ausser
 * Buchstaben/Ziffern/Slash/Leerzeichen entfernen, Whitespace kollabieren, auf 45
 * Zeichen kuerzen. Aendert sich das Original, MUSS diese Kopie mitwandern (beide
 * Seiten tragen dann eine neue `matcher_version`).
 */
object PatternKeyNormalizer {

    private const val MAX_LENGTH = 45

    private val MARKS = listOf(
        "eref", "mref", "cref", "iban", "bic", "beleg-nr", "vorgang", "transaktionsnummer"
    )

    private val NOISE = listOf(
        "gmbh", "ag", "kg", "mbh", "sepa", "lastschrift", "ueberweisung",
        "zahlung", "belastung", "kreditkarte", "card", "ref", "beleg", "vorgang",
        "transaktion", "ec", "pos", "gutschrift", "an", "von", "fuer", "und",
        "am", "im", "der", "die", "das", "mit", "bei", "fuer", "zum", "zur", "um"
    )

    private val reUhr = Regex("\\buhr\\b")
    private val reDigits = Regex("[0-9]+")
    private val rePunct = Regex("[^\\p{L}\\p{N}/ ]+")
    private val reSpace = Regex("\\s+")
    private val noiseRegexes = NOISE.map { Regex("\\b" + Regex.escape(it) + "\\b") }

    fun normalize(text: String): String {
        var t = text.trim().lowercase(Locale.ROOT)
        for (marke in MARKS) {
            val pos = t.indexOf(marke)
            if (pos > 0) t = t.substring(0, pos)
        }
        t = reUhr.replace(t, " ")
        t = reDigits.replace(t, " ")
        for (re in noiseRegexes) t = re.replace(t, " ")
        t = rePunct.replace(t, " ")
        t = reSpace.replace(t, " ")
        t = t.trim()
        return if (t.length > MAX_LENGTH) t.substring(0, MAX_LENGTH) else t
    }
}
