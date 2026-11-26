package com.sharek.macromandate.util

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class NutritionFormatTest {

    @Test
    fun formatGramsValuePreservesFractionsAndOmitsTrailingPointZero() {
        assertEquals("0", NutritionFormat.formatGramsValue(0f, Locale.US))
        assertEquals("40", NutritionFormat.formatGramsValue(40.0f, Locale.US))
        assertEquals("12.5", NutritionFormat.formatGramsValue(12.5f, Locale.US))
        assertEquals("12.7", NutritionFormat.formatGramsValue(12.66f, Locale.US))
        assertEquals("0", NutritionFormat.formatGramsValue(Float.NaN, Locale.US))
        assertEquals("0", NutritionFormat.formatGramsValue(Float.POSITIVE_INFINITY, Locale.US))
        assertEquals("0", NutritionFormat.formatGramsValue(-5f, Locale.US))
    }

    @Test
    fun formatGramsSumPreservesFractionalPrecisionBeforeRounding() {
        // Three 12.4g entries sum to 37.2g (previously truncated to 12+12+12 = 36g)
        val sumText = NutritionFormat.formatGramsSum(listOf(12.4f, 12.4f, 12.4f), Locale.US)
        assertEquals("37.2", sumText)
    }

    @Test
    fun sanitizeDecimalInputAcceptsCommaAndSingleDecimalPoint() {
        assertEquals("12.5", NutritionFormat.sanitizeDecimalInput("12,5"))
        assertEquals("12.5", NutritionFormat.sanitizeDecimalInput("12.5"))
        assertEquals("1.23", NutritionFormat.sanitizeDecimalInput("1.2.3"))
        assertEquals("45.67", NutritionFormat.sanitizeDecimalInput("45,6,7abc"))
        assertEquals("", NutritionFormat.sanitizeDecimalInput("abc"))
    }

    @Test
    fun isMealFormValidRequiresNameAndExplicitFiniteCalories() {
        assertFalse(NutritionFormat.isMealFormValid("", "450"))
        assertFalse(NutritionFormat.isMealFormValid("   ", "450"))
        assertFalse(NutritionFormat.isMealFormValid("Salmon Bowl", ""))
        assertFalse(NutritionFormat.isMealFormValid("Salmon Bowl", "abc"))
        assertFalse(NutritionFormat.isMealFormValid("Salmon Bowl", "25000"))

        // Explicit 0 kcal (e.g. water, black coffee) is valid when typed explicitly
        assertTrue(NutritionFormat.isMealFormValid("Black Coffee", "0"))
        assertTrue(NutritionFormat.isMealFormValid("Salmon Bowl", "550", "42.5", "48", "18.2"))
    }

    @Test
    fun estimateCaloriesFromMacrosUsesAtwaterFactors() {
        // 30g P * 4 + 40g C * 4 + 10g F * 9 = 120 + 160 + 90 = 370 kcal
        assertEquals(370, NutritionFormat.estimateCaloriesFromMacros(30f, 40f, 10f))
        assertEquals(0, NutritionFormat.estimateCaloriesFromMacros(0f, 0f, 0f))
    }

    @Test
    fun formatDateHeaderDistinguishesTodayAndYesterday() {
        val today = LocalDate.of(2026, 11, 25)
        val yesterday = LocalDate.of(2026, 11, 24)
        val earlier = LocalDate.of(2026, 11, 20)

        assertTrue(NutritionFormat.formatDateHeader(today, today, Locale.US).startsWith("TODAY ·"))
        assertTrue(NutritionFormat.formatDateHeader(yesterday, today, Locale.US).startsWith("YESTERDAY ·"))
        assertFalse(NutritionFormat.formatDateHeader(earlier, today, Locale.US).startsWith("TODAY"))
    }

    @Test
    fun dayWindowMillisCreatesHalfOpen24HourInterval() {
        val date = LocalDate.of(2026, 11, 25)
        val zone = ZoneId.of("UTC")
        val (start, end) = NutritionFormat.dayWindowMillis(date, zone)
        assertEquals(24L * 60 * 60 * 1000L, end - start)
    }
}
