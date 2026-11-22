package com.sharek.macromandate.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
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
    fun rejectsNegativeOnlyEstimates() {
        val json = """
            {
                "foodName": "Glitch Meal",
                "calories": -100,
                "proteinGrams": -20.0,
                "carbsGrams": -5.0,
                "fatGrams": -10.0
            }
        """.trimIndent()

        assertThrows(IllegalArgumentException::class.java) {
            NutritionSanitizer.parseAndSanitize(json)
        }
    }

    @Test
    fun rejectsEmptyUnrelatedAndWrongTypeResponses() {
        listOf(
            "{}",
            """{"status":"unavailable"}""",
            """{"foodName":"Soup","calories":0} {"foodName":"Extra","calories":1}""",
            """{"foodName":"Soup","calories":0,"extra":${"[".repeat(40)}0${"]".repeat(40)}}""",
            """{"foodName":"Soup","calories":true,"proteinGrams":[]}""",
            """{"foodName":{},"calories":0}"""
        ).forEach { json ->
            assertThrows(IllegalArgumentException::class.java) {
                NutritionSanitizer.parseAndSanitize(json)
            }
        }
    }

    @Test
    fun preservesExplicitZeroAndBoundsExtremeValues() {
        val water = NutritionSanitizer.parseAndSanitize("""{"foodName":"Water","calories":0}""")
        val estimate = NutritionSanitizer.parseAndSanitize(
            """{"foodName":"Shake","calories":9999999,"protein":1e100}"""
        )
        assertEquals(0, water.calories)
        assertEquals(20_000, estimate.calories)
        assertEquals(1_000f, estimate.proteinGrams, 0.01f)
    }

    @Test
    fun parseWithNanAndInfinityFallsBackSafely() {
        val json = """
            {
                "foodName": "Anomalous Food",
                "calories": "NaN",
                "proteinGrams": "Infinity",
                "carbsGrams": 20.0,
                "fatGrams": "-Infinity"
            }
        """.trimIndent()

        val parsed = NutritionSanitizer.parseAndSanitize(json)
        assertEquals("Anomalous Food", parsed.foodName)
        assertEquals(20f, parsed.carbsGrams, 0.01f)
        assertEquals(0f, parsed.proteinGrams, 0.01f)
        assertEquals(0f, parsed.fatGrams, 0.01f)
        assertEquals(80, parsed.calories) // 20g carbs * 4 = 80 kcal fallback
    }

    @Test
    fun parseAlternativeKeyNames() {
        val json = """
            {
                "item": "Keto Avocado Bowl",
                "energy": "450 kcal",
                "protein": "8.5g",
                "carbohydrates": "4.2g",
                "fat": "38.0g",
                "beverage": "true",
                "confidence": "HIGH_CONFIDENCE"
            }
        """.trimIndent()

        val parsed = NutritionSanitizer.parseAndSanitize(json)
        assertEquals("Keto Avocado Bowl", parsed.foodName)
        assertEquals(450, parsed.calories)
        assertEquals(8.5f, parsed.proteinGrams, 0.01f)
        assertEquals(4.2f, parsed.carbsGrams, 0.01f)
        assertEquals(38f, parsed.fatGrams, 0.01f)
        assertTrue(parsed.isLiquid)
        assertEquals("HIGH_CONFIDENCE", parsed.confidence)
    }

    @Test
    fun parseExtremeValuesAndDecimals() {
        val json = """
            {
                "name": "Heavy Shake",
                "calories": 1500,
                "protein": " 75.25 G ",
                "carbs": " 120.50 g ",
                "fat": " 45.75 g "
            }
        """.trimIndent()

        val parsed = NutritionSanitizer.parseAndSanitize(json)
        assertEquals("Heavy Shake", parsed.foodName)
        assertEquals(1500, parsed.calories)
        assertEquals(75.25f, parsed.proteinGrams, 0.01f)
        assertEquals(120.50f, parsed.carbsGrams, 0.01f)
        assertEquals(45.75f, parsed.fatGrams, 0.01f)
    }
}
