package com.sharek.macromandate.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionSanitizerTest {

    @Test
    fun parseStandardValidJson() {
        val json = """
            {
                "foodName": "Grilled Salmon",
                "calories": 420,
                "proteinGrams": 40.0,
                "carbsGrams": 0.0,
                "fatGrams": 28.0,
                "isLiquid": false,
                "assessment": "OPTIMAL PROTEIN SYNTHESIS."
            }
        """.trimIndent()

        val parsed = NutritionSanitizer.parseAndSanitize(json)
        assertEquals("Grilled Salmon", parsed.foodName)
        assertEquals(420, parsed.calories)
        assertEquals(40f, parsed.proteinGrams, 0.01f)
        assertEquals(0f, parsed.carbsGrams, 0.01f)
        assertEquals(28f, parsed.fatGrams, 0.01f)
        assertFalse(parsed.isLiquid)
        assertEquals("OPTIMAL PROTEIN SYNTHESIS.", parsed.assessment)
    }

    @Test
    fun parseMissingCaloriesWithMacrosCalculatesEstimate() {
        val json = """
            {
                "foodName": "Protein Shake",
                "calories": 0,
                "proteinGrams": 30.0,
                "carbsGrams": 10.0,
                "fatGrams": 2.0,
                "isLiquid": true
            }
        """.trimIndent()

        // 30*4 + 10*4 + 2*9 = 120 + 40 + 18 = 178 kcal
        val parsed = NutritionSanitizer.parseAndSanitize(json)
        assertEquals("Protein Shake", parsed.foodName)
        assertEquals(178, parsed.calories)
        assertEquals(30f, parsed.proteinGrams, 0.01f)
        assertTrue(parsed.isLiquid)
    }

    @Test
    fun parseStringAndFloatFormulasGracefully() {
        val json = """
            {
                "name": "Oatmeal Bowl",
                "calories": "350 kcal",
                "protein": "12.5g",
                "carbs": "55.0g",
                "fat": "6.2g",
                "type": "solid"
            }
        """.trimIndent()

        val parsed = NutritionSanitizer.parseAndSanitize(json)
        assertEquals("Oatmeal Bowl", parsed.foodName)
        assertEquals(350, parsed.calories)
        assertEquals(12.5f, parsed.proteinGrams, 0.01f)
        assertEquals(55f, parsed.carbsGrams, 0.01f)
        assertEquals(6.2f, parsed.fatGrams, 0.01f)
        assertFalse(parsed.isLiquid)
    }

    @Test
    fun parseNegativeValuesCoercedToZero() {
        val json = """
            {
                "foodName": "Glitch Meal",
                "calories": -100,
                "proteinGrams": -20.0,
                "carbsGrams": -5.0,
                "fatGrams": -10.0
            }
        """.trimIndent()

        val parsed = NutritionSanitizer.parseAndSanitize(json)
        assertEquals("Glitch Meal", parsed.foodName)
        assertEquals(0, parsed.calories)
        assertEquals(0f, parsed.proteinGrams, 0.01f)
        assertEquals(0f, parsed.carbsGrams, 0.01f)
        assertEquals(0f, parsed.fatGrams, 0.01f)
    }

    @Test
    fun parseEmptyFieldsFallbackGracefully() {
        val json = "{}"

        val parsed = NutritionSanitizer.parseAndSanitize(json)
        assertEquals("Unidentified Item", parsed.foodName)
        assertEquals(0, parsed.calories)
        assertEquals(0f, parsed.proteinGrams, 0.01f)
        assertEquals(0f, parsed.carbsGrams, 0.01f)
        assertEquals(0f, parsed.fatGrams, 0.01f)
        assertFalse(parsed.isLiquid)
        assertEquals("NOMINAL REFUELING REGISTERED.", parsed.assessment)
    }
}
