package de.mybudgets.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Prueft, dass [PatternKeyNormalizer] dieselben Schluessel bildet wie finn
 * `insights_normalisieren()`. Die Erwartungswerte sind von Hand gegen die
 * PHP-Funktion nachgerechnet - weichen sie ab, treffen Dashboard-Overrides
 * die App-Buchungen nicht mehr.
 */
class PatternKeyNormalizerTest {

    @Test
    fun removesDigitsAndKeepsWords() {
        assertEquals("rewe sagt danke", PatternKeyNormalizer.normalize("REWE SAGT DANKE 1234"))
    }

    @Test
    fun lowercasesAndCollapsesWhitespace() {
        assertEquals("bäcker meier", PatternKeyNormalizer.normalize("  Bäcker   Meier "))
    }

    @Test
    fun removesBankNoiseWords() {
        assertEquals(
            "überweisung max mustermann",
            PatternKeyNormalizer.normalize("Überweisung an Max Mustermann 4711")
        )
    }

    @Test
    fun stripsPunctuationButKeepsSlash() {
        assertEquals("amazon eu s a r l", PatternKeyNormalizer.normalize("Lastschrift von AMAZON EU S.a.r.l. 1234567890"))
        assertEquals("s/ubahn", PatternKeyNormalizer.normalize("S/UBahn"))
    }

    @Test
    fun cutsAtBankReferenceMarkers() {
        assertEquals("rewe markt", PatternKeyNormalizer.normalize("REWE MARKT IBAN DE12345678901234567890"))
        assertEquals("kartenzahlung", PatternKeyNormalizer.normalize("Kartenzahlung Beleg-Nr 998877"))
    }

    @Test
    fun truncatesTo45Chars() {
        assertEquals("a".repeat(45), PatternKeyNormalizer.normalize("a".repeat(60)))
    }

    @Test
    fun emptyAndBlankBecomeEmpty() {
        assertEquals("", PatternKeyNormalizer.normalize("   "))
        assertEquals("", PatternKeyNormalizer.normalize("4711"))
    }
}
