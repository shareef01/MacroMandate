package com.sharek.macromandate.ui

import com.sharek.macromandate.model.MealEntry
import com.sharek.macromandate.util.NutritionSanitizer
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class AnalyticsAndMapTest {

    private fun geotaggedMeal(id: String, lat: Double, lon: Double) = MealEntry(
        id = id,
        timestamp = 1_700_000_000_000L,
        imageUri = null,
        foodName = "Geotagged Meal $id",
        calories = 500,
        proteinGrams = 30f,
        carbsGrams = 40f,
        fatGrams = 15f,
        isLiquid = false,
        latitude = lat,
        longitude = lon
    )

    @Test
    fun mapReferencePreservesWholeDegreesAndComputesAccurateMeterOffsets() {
        // Two meals ~111 meters apart along latitude (0.001 deg lat ≈ 111.32m)
        val m1 = geotaggedMeal("1", 52.5200, 13.4050)
        val m2 = geotaggedMeal("2", 52.5210, 13.4050)
        val ref = MapReference.of(listOf(m1, m2))
        assertNotNull(ref)
        ref!!

        val (dx1, dy1) = ref.offsetMeters(m1.latitude!!, m1.longitude!!)
        val (dx2, dy2) = ref.offsetMeters(m2.latitude!!, m2.longitude!!)
        assertEquals(0.0, dx1, 0.1)
        assertEquals(0.0, dx2, 0.1)
        // Relative north-south distance should be ~111.32 meters
        assertEquals(111.32, dy2 - dy1, 1.0)
        assertTrue(ref.gridStepMeters >= 10)
    }

    @Test
    fun mapReferenceReturnsNullWhenNoGeotaggedMealsExist() {
        val unGeotagged = MealEntry(
            id = "no-geo",
            timestamp = 1_700_000_000_000L,
            imageUri = null,
            foodName = "Oatmeal",
            calories = 300,
            proteinGrams = 10f,
            carbsGrams = 50f,
            fatGrams = 5f,
            isLiquid = false
        )
        assertNull(MapReference.of(listOf(unGeotagged)))
    }

    @Test
    fun dailyBucketDistinguishesUnrecordedDayFromRecordedZeroCalorieDay() {
        val unrecordedDay = DailyBucket(
            date = LocalDate.of(2026, 11, 24),
            shortLabel = "TUE",
            calories = 0,
            proteinGrams = 0f,
            carbsGrams = 0f,
            fatGrams = 0f,
            mealCount = 0
        )
        val zeroCalorieRecordedDay = DailyBucket(
            date = LocalDate.of(2026, 11, 25),
            shortLabel = "WED",
            calories = 0,
            proteinGrams = 0f,
            carbsGrams = 0f,
            fatGrams = 0f,
            mealCount = 2 // e.g. Water and Black Coffee
        )

        assertFalse(unrecordedDay.hasRecordedMeals)
        assertTrue(zeroCalorieRecordedDay.hasRecordedMeals)
    }

    @Test
    fun nutritionSanitizerFlagsDerivedMismatchAndClampedCaveats() {
        val derived = NutritionSanitizer.parseAndSanitize(
            """{"foodName":"Chicken Breast","proteinGrams":40,"carbsGrams":0,"fatGrams":5}"""
        )
        assertTrue(derived.isCaloriesDerived)
        assertFalse(derived.hasMacroCalorieMismatch)
        assertFalse(derived.wasClamped)

        val mismatch = NutritionSanitizer.parseAndSanitize(
            """{"foodName":"Anomaly","calories":100,"proteinGrams":80,"carbsGrams":50,"fatGrams":20}"""
        )
        assertFalse(mismatch.isCaloriesDerived)
        assertTrue(mismatch.hasMacroCalorieMismatch)

        val clamped = NutritionSanitizer.parseAndSanitize(
            """{"foodName":"Huge Entry","calories":50000,"proteinGrams":2000}"""
        )
        assertTrue(clamped.wasClamped)
    }
}
