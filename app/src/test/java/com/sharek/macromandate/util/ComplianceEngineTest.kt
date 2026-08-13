package com.sharek.macromandate.util

import com.sharek.macromandate.model.MealEntry
import com.sharek.macromandate.viewmodel.ComplianceStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ComplianceEngineTest {

    private val baseTimestamp = 1_700_000_000_000L // 2023-11-14, a fixed day
    private val otherDayTimestamp = 1_702_000_000_000L // 2023-12-07, a different day

    private fun meal(calories: Int, timestamp: Long = baseTimestamp, foodName: String = "Test Fuel") =
        MealEntry(
            id = "id-$calories-$timestamp",
            timestamp = timestamp,
            imageUri = null,
            foodName = foodName,
            calories = calories,
            proteinGrams = 0f,
            carbsGrams = 0f,
            fatGrams = 0f,
            isLiquid = false
        )

    @Test
    fun emptyLogIsPerfectScore() {
        assertEquals(100, ComplianceEngine.calculateScore(emptyList(), 2500))
    }

    @Test
    fun atTargetIsPerfectScore() {
        assertEquals(100, ComplianceEngine.calculateScore(listOf(meal(2500)), 2500))
    }

    @Test
    fun overTargetReducesScore() {
        // 3000 vs 2500 = 20% deviation -> 80
        assertEquals(80, ComplianceEngine.calculateScore(listOf(meal(3000)), 2500))
    }

    @Test
    fun underTargetReducesScore() {
        // 2000 vs 2500 = 20% deviation -> 80
        assertEquals(80, ComplianceEngine.calculateScore(listOf(meal(2000)), 2500))
    }

    @Test
    fun scoreNeverDropsBelowZero() {
        assertEquals(0, ComplianceEngine.calculateScore(listOf(meal(10000)), 2500))
    }

    @Test
    fun multipleMealsOnSameDayAreAggregated() {
        val dayTotaling2500 = listOf(meal(1000), meal(1500))
        assertEquals(100, ComplianceEngine.calculateScore(dayTotaling2500, 2500))
    }

    @Test
    fun deviationAveragedAcrossDays() {
        val meals = listOf(meal(3000, baseTimestamp), meal(3000, otherDayTimestamp))
        // Both days deviate 20% -> average 20% -> 80
        assertEquals(80, ComplianceEngine.calculateScore(meals, 2500))
    }

    @Test
    fun zeroTargetDoesNotDivideByZero() {
        assertEquals(100, ComplianceEngine.calculateScore(listOf(meal(2500)), 0))
    }

    @Test
    fun statusMapping() {
        assertEquals(ComplianceStatus.EXEMPLARY, ComplianceEngine.statusFor(90))
        assertEquals(ComplianceStatus.EXEMPLARY, ComplianceEngine.statusFor(100))
        assertEquals(ComplianceStatus.ACCEPTABLE, ComplianceEngine.statusFor(70))
        assertEquals(ComplianceStatus.ACCEPTABLE, ComplianceEngine.statusFor(89))
        assertEquals(ComplianceStatus.SUBVERSIVE, ComplianceEngine.statusFor(40))
        assertEquals(ComplianceStatus.SUBVERSIVE, ComplianceEngine.statusFor(69))
        assertEquals(ComplianceStatus.CRISIS, ComplianceEngine.statusFor(0))
        assertEquals(ComplianceStatus.CRISIS, ComplianceEngine.statusFor(39))
    }
}
