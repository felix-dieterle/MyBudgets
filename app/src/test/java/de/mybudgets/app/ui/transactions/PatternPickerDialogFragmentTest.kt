package de.mybudgets.app.ui.transactions

import org.junit.Assert.assertEquals
import org.junit.Test

class PatternPickerDialogFragmentTest {

    private val fragment = PatternPickerDialogFragment()

    @Test
    fun `extractKeywords from v dot numeric reference`() {
        val result = fragment.extractKeywords("v.12121423")
        assertEquals(listOf("12121423"), result)
    }

    @Test
    fun `extractKeywords from description with reference and store name`() {
        val result = fragment.extractKeywords("V.12121423 EDEKA MÜNCHEN")
        assertEquals(listOf("12121423", "edeka", "münchen"), result)
    }

    @Test
    fun `extractKeywords from domain description`() {
        val result = fragment.extractKeywords("EDEKA.DE EINKAUF")
        assertEquals(listOf("edeka", "einkauf"), result)
    }

    @Test
    fun `extractKeywords rejects stopwords`() {
        val result = fragment.extractKeywords("mit von einkauf")
        assertEquals(listOf("einkauf"), result)
    }

    @Test
    fun `extractKeywords rejects words shorter than 3 chars`() {
        val result = fragment.extractKeywords("ab cd ef g")
        assertEquals(emptyList<String>(), result)
    }

    @Test
    fun `extractKeywords includes 3 char words`() {
        val result = fragment.extractKeywords("pay pal abc")
        assertEquals(listOf("pay", "pal", "abc"), result)
    }

    @Test
    fun `extractKeywords includes all distinct words`() {
        val result = fragment.extractKeywords("a b c d e f g h i j k l m n o p")
        val words = result.toSet()
        assertEquals(words.size, result.size)
    }

    @Test
    fun `extractKeywords handles empty string`() {
        val result = fragment.extractKeywords("")
        assertEquals(emptyList<String>(), result)
    }
}
