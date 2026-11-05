package dev.mike.fierolauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class T9Test {

    private fun matches(label: String, typed: String) = t9Matches(toT9(label), typed)

    @Test
    fun lettersMapToKeypadDigits() {
        assertEquals("6277", toT9("Maps"))
        assertEquals("22233344455566677778889999", toT9("abcdefghijklmnopqrstuvwxyz"))
        assertEquals("6277", toT9("MAPS")) // case-insensitive
    }

    @Test
    fun digitsStayAndEverythingElseIsAWordBreak() {
        assertEquals("1253905392", toT9("12Key-Keyboard").take(10))
        assertEquals("3037643", toT9("F-Droid"))
        assertEquals("6277054830", toT9("Maps Lite "))
    }

    @Test
    fun emptyFilterMatchesEverything() {
        assertTrue(matches("Anything", ""))
    }

    @Test
    fun matchesStartOfFirstWord() {
        assertTrue(matches("Maps Go", "62"))
        assertTrue(matches("Maps Go", "6277"))
        assertFalse(matches("Maps Go", "277")) // middle of a word
    }

    @Test
    fun matchesStartOfLaterWords() {
        assertTrue(matches("File manager", "62")) // "manager"
        assertTrue(matches("Call history", "4478")) // "hist"
        assertTrue(matches("F-Droid", "376")) // "Dro" after the hyphen
    }

    @Test
    fun zeroInFilterSpansAWordBreak() {
        assertTrue(matches("Maps Lite", "6277054"))
        assertTrue(matches("Maps  Lite", "62770054")) // two spaces = two breaks
        assertFalse(matches("MapsLite", "6277054"))
    }

    @Test
    fun filterLongerThanLabelFails() {
        assertFalse(matches("Go", "4666"))
    }

    @Test
    fun digitsInLabelsMatchThemselves() {
        assertTrue(matches("12Key-Keyboard", "12"))
        assertTrue(matches("12Key-Keyboard", "539")) // "Keyboard" after the hyphen
    }

    @Test
    fun leadingSeparatorsDoNotCountAsAWord() {
        // "(Beta) App": the '(' becomes '0'; the word starts at 'B'.
        assertTrue(matches("(Beta) App", "2382"))
        assertTrue(matches("(Beta) App", "277"))
    }
}
