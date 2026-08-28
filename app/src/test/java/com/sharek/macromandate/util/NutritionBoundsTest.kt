package com.sharek.macromandate.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shared ingestion gate. Every path that writes nutrition into Room — model
 * output, manual entry, the edit dialog, JSON restore — goes through these, so
 * a value rejected on one route cannot arrive via another.
 */
class NutritionBoundsTest {

    // ---- calories -----------------------------------------------------------

    @Test
    fun caloriesAreClampedToTheCeiling() {
        assertEquals(NutritionBounds.MAX_CALORIES, NutritionBounds.clampCalories(Int.MAX_VALUE))
        assertEquals(NutritionBounds.MAX_CALORIES, NutritionBounds.clampCalories(250_000))
    }

    @Test
    fun negativeCaloriesBecomeZero() {
        assertEquals(0, NutritionBounds.clampCalories(-1))
        assertEquals(0, NutritionBounds.clampCalories(Int.MIN_VALUE))
    }

    @Test
    fun ordinaryCaloriesPassThroughUnchanged() {
        assertEquals(420, NutritionBounds.clampCalories(420))
    }

    @Test
    fun nonFiniteCaloriesCollapseToZeroRatherThanPropagating() {
        // A NaN reaching Room poisons every daily and weekly sum that touches it.
        assertEquals(0, NutritionBounds.clampCalories(Double.NaN))
        assertEquals(0, NutritionBounds.clampCalories(Double.NEGATIVE_INFINITY))
        assertEquals(NutritionBounds.MAX_CALORIES, NutritionBounds.clampCalories(Double.POSITIVE_INFINITY))
    }

    @Test
    fun fractionalCaloriesRoundRatherThanTruncate() {
        assertEquals(421, NutritionBounds.clampCalories(420.6))
    }

    // ---- macros -------------------------------------------------------------

    @Test
    fun gramsAreClampedAtBothEnds() {
        assertEquals(0f, NutritionBounds.clampGrams(-5f), 0.001f)
        assertEquals(NutritionBounds.MAX_MACRO_GRAMS, NutritionBounds.clampGrams(1e9f), 0.001f)
        assertEquals(42.5f, NutritionBounds.clampGrams(42.5f), 0.001f)
    }

    @Test
    fun nonFiniteGramsCollapseToZero() {
        assertEquals(0f, NutritionBounds.clampGrams(Float.NaN), 0.001f)
        assertEquals(0f, NutritionBounds.clampGrams(Float.NEGATIVE_INFINITY), 0.001f)
        assertEquals(NutritionBounds.MAX_MACRO_GRAMS, NutritionBounds.clampGrams(Float.POSITIVE_INFINITY), 0.001f)
    }

    // ---- strings ------------------------------------------------------------

    @Test
    fun namesAreTruncatedNotRejected() {
        val long = "x".repeat(5_000)
        assertEquals(NutritionBounds.MAX_NAME_LENGTH, NutritionBounds.clampName(long, "fallback").length)
    }

    @Test
    fun blankAndNullNamesFallBack() {
        assertEquals("fallback", NutritionBounds.clampName("   ", "fallback"))
        assertEquals("fallback", NutritionBounds.clampName(null, "fallback"))
    }

    @Test
    fun unicodeNamesSurviveIntact() {
        assertEquals("🍜 拉麵", NutritionBounds.clampName("  🍜 拉麵  ", "fallback"))
    }

    @Test
    fun blankAssessmentBecomesNullRatherThanEmptyString() {
        assertEquals(null, NutritionBounds.clampAssessment("  "))
        assertEquals(null, NutritionBounds.clampAssessment(null))
        assertEquals("Fine.", NutritionBounds.clampAssessment(" Fine. "))
    }

    // ---- Atwater fallback ---------------------------------------------------

    @Test
    fun caloriesFromMacrosUsesFourFourNine() {
        // 10p*4 + 20c*4 + 5f*9 = 40 + 80 + 45 = 165
        assertEquals(165, NutritionBounds.caloriesFromMacros(10f, 20f, 5f))
    }

    @Test
    fun caloriesFromMacrosIsItselfClamped() {
        assertEquals(
            NutritionBounds.MAX_CALORIES,
            NutritionBounds.caloriesFromMacros(2_000f, 2_000f, 2_000f)
        )
    }

    // ---- self-contradiction detection ---------------------------------------

    @Test
    fun agreeingCaloriesAndMacrosAreNotFlagged() {
        // 165 kcal from macros; a stated 170 is ordinary rounding.
        assertFalse(NutritionBounds.caloriesContradictMacros(170, 10f, 20f, 5f))
    }

    @Test
    fun caloriesFarBelowTheMacrosAreFlagged() {
        // 40g protein alone is 160 kcal; 20 kcal is impossible.
        assertTrue(NutritionBounds.caloriesContradictMacros(20, 40f, 0f, 0f))
    }

    @Test
    fun caloriesFarAboveTheMacrosAreFlagged() {
        assertTrue(NutritionBounds.caloriesContradictMacros(5_000, 10f, 20f, 5f))
    }

    @Test
    fun noMacrosMeansNothingToContradict() {
        // Plenty of real responses give only a calorie figure.
        assertFalse(NutritionBounds.caloriesContradictMacros(600, 0f, 0f, 0f))
    }
}
