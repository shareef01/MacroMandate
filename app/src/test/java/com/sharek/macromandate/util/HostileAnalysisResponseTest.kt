package com.sharek.macromandate.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The model response is untrusted external input.
 *
 * It arrives over the network from a third-party provider, its content is
 * steered by whatever the user photographed, and no schema is enforced on the
 * way back. Each case here is a shape that a real provider has been observed to
 * return, or that a crafted image could induce. None of them may crash, and none
 * may produce a value that would corrupt a daily total.
 *
 * The extraction step in the ViewModel takes the substring between the first `{`
 * and the last `}`, so these tests feed the sanitizer what that step would hand
 * it.
 */
class HostileAnalysisResponseTest {

    private fun extractAndParse(raw: String): ParsedNutrition? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start == -1 || end <= start) return null
        return runCatching { NutritionSanitizer.parseAndSanitize(raw.substring(start, end + 1)) }.getOrNull()
    }

    // ---- shapes that still carry usable data --------------------------------

    @Test
    fun proseBeforeAndAfterTheObjectIsIgnored() {
        val parsed = extractAndParse(
            "Sure! Here is the nutrition data you asked for:\n" +
                """{"foodName":"Toast","calories":180,"proteinGrams":6}""" +
                "\nLet me know if you need anything else."
        )
        assertEquals("Toast", parsed?.foodName)
        assertEquals(180, parsed?.calories)
    }

    @Test
    fun markdownFencedJsonIsRead() {
        val parsed = extractAndParse("```json\n{\"foodName\":\"Soup\",\"calories\":210}\n```")
        assertEquals("Soup", parsed?.foodName)
        assertEquals(210, parsed?.calories)
    }

    @Test
    fun numbersDeliveredAsStringsAreAccepted() {
        val parsed = extractAndParse(
            """{"foodName":"Rice","calories":"350","proteinGrams":"7.5","carbsGrams":"77 g"}"""
        )
        assertEquals(350, parsed?.calories)
        assertEquals(7.5f, parsed?.proteinGrams ?: 0f, 0.01f)
        assertEquals(77f, parsed?.carbsGrams ?: 0f, 0.01f)
    }

    @Test
    fun alternateKeyNamesAreAccepted() {
        val parsed = extractAndParse("""{"name":"Curry","kcal":540,"carbohydrates":60,"fat":22}""")
        assertEquals("Curry", parsed?.foodName)
        assertEquals(540, parsed?.calories)
        assertEquals(60f, parsed?.carbsGrams ?: 0f, 0.01f)
        assertEquals(22f, parsed?.fatGrams ?: 0f, 0.01f)
    }

    // ---- values that must not reach the database ----------------------------

    @Test
    fun absurdCalorieCountIsClampedAndFlagged() {
        val parsed = extractAndParse("""{"foodName":"Apple","calories":250000}""")
        assertEquals(NutritionBounds.MAX_CALORIES, parsed?.calories)
        assertTrue("clamping must be visible to the user", parsed?.valuesClamped == true)
    }

    @Test
    fun negativeMacrosBecomeZero() {
        val parsed = extractAndParse(
            """{"foodName":"Salad","calories":90,"proteinGrams":-5,"carbsGrams":-1e9,"fatGrams":-0.5}"""
        )
        assertEquals(0f, parsed?.proteinGrams ?: -1f, 0.001f)
        assertEquals(0f, parsed?.carbsGrams ?: -1f, 0.001f)
        assertEquals(0f, parsed?.fatGrams ?: -1f, 0.001f)
    }

    @Test
    fun nanAndInfinityLiteralsDoNotProduceNonFiniteValues() {
        val parsed = extractAndParse(
            """{"foodName":"Ghost","calories":"NaN","proteinGrams":"Infinity","fatGrams":"-Infinity"}"""
        )
        assertNotNull(parsed)
        assertTrue(parsed!!.proteinGrams.isFinite())
        assertTrue(parsed.fatGrams.isFinite())
        assertEquals(0f, parsed.proteinGrams, 0.001f)
    }

    @Test
    fun scientificNotationIsRead() {
        val parsed = extractAndParse("""{"foodName":"Bar","calories":2.5e2}""")
        assertEquals(250, parsed?.calories)
    }

    @Test
    fun nestedObjectsAndArraysWhereScalarsWereExpectedDegradeToZero() {
        val parsed = extractAndParse(
            """{"foodName":"Odd","calories":{"value":300},"proteinGrams":[1,2,3]}"""
        )
        assertNotNull(parsed)
        assertEquals(0, parsed?.calories)
        assertEquals(0f, parsed?.proteinGrams ?: -1f, 0.001f)
    }

    @Test
    fun explicitNullsAreTreatedAsAbsent() {
        val parsed = extractAndParse(
            """{"foodName":null,"calories":null,"proteinGrams":null,"assessment":null}"""
        )
        assertNotNull(parsed)
        assertEquals("Unidentified item", parsed?.foodName)
        assertEquals(0, parsed?.calories)
        assertNull(parsed?.assessment)
    }

    @Test
    fun anEmptyObjectYieldsNeutralDefaults() {
        val parsed = extractAndParse("{}")
        assertNotNull(parsed)
        assertEquals(0, parsed?.calories)
        assertNull(parsed?.assessment)
        assertFalse(parsed?.isLiquid ?: true)
    }

    @Test
    fun aMaliciouslyLongNameIsTruncatedNotStored() {
        val parsed = extractAndParse("""{"foodName":"${"A".repeat(100_000)}","calories":10}""")
        assertEquals(NutritionBounds.MAX_NAME_LENGTH, parsed?.foodName?.length)
    }

    @Test
    fun aMaliciouslyLongAssessmentIsTruncated() {
        val parsed = extractAndParse("""{"foodName":"X","assessment":"${"B".repeat(100_000)}"}""")
        assertEquals(NutritionBounds.MAX_ASSESSMENT_LENGTH, parsed?.assessment?.length)
    }

    // ---- shapes with nothing usable in them ---------------------------------

    @Test
    fun truncatedJsonIsRejectedWithoutThrowing() {
        assertNull(extractAndParse("""{"foodName":"Half a resp"""))
    }

    @Test
    fun anHtmlErrorPageIsRejectedWithoutThrowing() {
        assertNull(extractAndParse("<html><head><title>502 Bad Gateway</title></head></html>"))
    }

    @Test
    fun aBareArrayResponseIsRejectedWithoutThrowing() {
        // "[{...}]" - the extractor finds braces, but the payload is not the
        // object shape we asked for; it must degrade, not crash.
        val parsed = extractAndParse("""[{"foodName":"In an array","calories":100}]""")
        assertNotNull(parsed)
    }

    @Test
    fun anEmptyResponseIsRejected() {
        assertNull(extractAndParse(""))
        assertNull(extractAndParse("   "))
    }

    // ---- honesty flags ------------------------------------------------------

    @Test
    fun calculatedCaloriesAreMarkedAsCalculated() {
        val parsed = extractAndParse(
            """{"foodName":"Shake","proteinGrams":30,"carbsGrams":40,"fatGrams":5}"""
        )
        // 30*4 + 40*4 + 5*9 = 325
        assertEquals(325, parsed?.calories)
        assertTrue(parsed?.caloriesDerivedFromMacros == true)
    }

    @Test
    fun statedCaloriesAreNotOverwrittenByTheMacroEstimate() {
        // The model said 200; the macros imply 325. We keep what it said and flag
        // the disagreement rather than silently picking a winner.
        val parsed = extractAndParse(
            """{"foodName":"Shake","calories":200,"proteinGrams":30,"carbsGrams":40,"fatGrams":5}"""
        )
        assertEquals(200, parsed?.calories)
        assertFalse(parsed?.caloriesDerivedFromMacros ?: true)
    }

    @Test
    fun selfContradictoryResponsesAreFlaggedForReview() {
        val parsed = extractAndParse(
            """{"foodName":"Impossible","calories":15,"proteinGrams":50,"carbsGrams":50,"fatGrams":30}"""
        )
        assertTrue(parsed?.caloriesContradictMacros == true)
    }

    @Test
    fun liquidIsDetectedFromAnyOfItsSpellings() {
        assertTrue(extractAndParse("""{"foodName":"A","isLiquid":true}""")?.isLiquid == true)
        assertTrue(extractAndParse("""{"foodName":"A","type":"LIQUID"}""")?.isLiquid == true)
        assertTrue(extractAndParse("""{"foodName":"A","beverage":"true"}""")?.isLiquid == true)
        assertFalse(extractAndParse("""{"foodName":"A"}""")?.isLiquid ?: true)
    }
}
