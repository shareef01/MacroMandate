package com.sharek.macromandate.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Display and input handling for nutrition numbers.
 *
 * These guard two classes of silent data loss that the app shipped with: macro
 * values truncated on the way out, and typed values discarded on the way in.
 */
class NutritionFormatTest {

    // ---- display -------------------------------------------------------------

    @Test
    fun fractionalGramsRoundRatherThanTruncate() {
        // The screens called .toInt() on these, so 12.7 g displayed as "12 g".
        assertEquals("12.7", formatGramsValue(12.7f))
        assertEquals("12.8", formatGramsValue(12.75f))
    }

    @Test
    fun wholeNumbersDropThePointZero() {
        assertEquals("40", formatGramsValue(40f))
        assertEquals("0", formatGramsValue(0f))
        // Float.toString() would seed an edit field with "40.0".
        assertEquals("40", formatGramsValue(40.0f))
    }

    @Test
    fun nonFiniteValuesRenderAsZeroRatherThanNaN() {
        assertEquals("0", formatGramsValue(Float.NaN))
        assertEquals("0", formatGramsValue(Float.POSITIVE_INFINITY))
    }

    @Test
    fun gramsIncludeTheUnit() {
        assertEquals("12.7 g", formatGrams(12.7f))
    }

    // ---- screen reader -------------------------------------------------------

    @Test
    fun macrosAreSpokenAsWordsNotAbbreviations() {
        // The visible row reads "P: 40g C: 12g F: 8g", which TalkBack announces
        // as "P colon forty g" - meaningless without the colour and column cues.
        assertEquals(
            "Protein 40 grams. Carbohydrates 12 grams. Fat 8 grams.",
            macroContentDescription(40f, 12f, 8f)
        )
    }

    // ---- input ---------------------------------------------------------------

    @Test
    fun commaIsAcceptedAsADecimalSeparator() {
        // On a comma-decimal keyboard the old filter dropped the separator
        // outright, turning "12,5" into "125".
        assertEquals("12.5", sanitizeDecimalInput("12,5"))
        assertEquals(12.5f, parseGrams(sanitizeDecimalInput("12,5")), 0.001f)
    }

    @Test
    fun onlyTheFirstSeparatorIsKept() {
        assertEquals("1.23", sanitizeDecimalInput("1.2.3"))
        assertEquals("1.23", sanitizeDecimalInput("1,2,3"))
    }

    @Test
    fun lettersAndSignsAreStripped() {
        assertEquals("40", sanitizeDecimalInput("-40abc"))
        assertEquals("", sanitizeDecimalInput("abc"))
    }

    @Test
    fun inputLengthIsBoundedSoParsingCannotOverflow() {
        assertEquals(7, sanitizeDecimalInput("9".repeat(50)).length)
    }

    @Test
    fun unparsableGramsBecomeZero() {
        assertEquals(0f, parseGrams(""), 0.001f)
        assertEquals(0f, parseGrams("."), 0.001f)
    }

    @Test
    fun overlongCalorieInputDoesNotSilentlyBecomeZero() {
        // toIntOrNull() returns null past Int.MAX_VALUE and the dialogs mapped
        // that null to 0, so a long number logged a zero-calorie meal.
        assertEquals(Int.MAX_VALUE, parseCalories("999999999999999"))
        assertEquals(2500, parseCalories("2500"))
        assertNull(parseCalories(""))
        assertNull(parseCalories("abc"))
    }
}
