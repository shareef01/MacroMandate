package com.sharek.macromandate.util

import com.sharek.macromandate.model.MealEntry
import com.sharek.macromandate.viewmodel.ComplianceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DossierReportGeneratorTest {

    private fun sampleMeal(
        foodName: String,
        calories: Int,
        protein: Float = 20f,
        carbs: Float = 30f,
        fat: Float = 10f,
        isLiquid: Boolean = false,
        isRestricted: Boolean = false,
        isNightRefueling: Boolean = false
    ) = MealEntry(
        id = "meal_test_${System.currentTimeMillis()}",
        timestamp = 1_700_000_000_000L,
        imageUri = null,
        foodName = foodName,
        calories = calories,
        proteinGrams = protein,
        carbsGrams = carbs,
        fatGrams = fat,
        isLiquid = isLiquid,
        latitude = if (isRestricted) 37.7749 else null,
        longitude = if (isRestricted) -122.4194 else null,
        assessment = "TEST ASSESSMENT",
        isRestricted = isRestricted,
        isNightRefueling = isNightRefueling
    )

    @Test
    fun generateWeeklyMarkdownWithNoMeals() {
        val report = DossierReportGenerator.generateWeeklyMarkdown(
            meals = emptyList(),
            calorieTarget = 2000,
            complianceScore = 100,
            complianceStatus = ComplianceStatus.EXEMPLARY,
            generatedTimestamp = 1_700_000_000_000L
        )

        assertTrue(report.contains("MACROMANDATE // WEEKLY SURVEILLANCE DOSSIER"))
        assertTrue(report.contains("DAILY CALORIE TARGET : 2000 kcal"))
        assertTrue(report.contains("TOTAL 7-DAY INTAKE   : 0 kcal across 0 meals"))
        assertTrue(report.contains("STATUS VERDICT      : EXEMPLARY (100/100)"))
        assertTrue(report.contains("ZONE RESTRICTION INFRACTIONS : 0"))
        assertTrue(report.contains("COMMENDATION: Subject demonstrates strict metabolic discipline."))
    }

    @Test
    fun generateWeeklyMarkdownWithAdherentMeals() {
        val meals = listOf(
            sampleMeal("Grilled Chicken", 500, protein = 50f, carbs = 10f, fat = 15f),
            sampleMeal("Protein Shake", 200, protein = 35f, carbs = 5f, fat = 2f, isLiquid = true),
            sampleMeal("Oatmeal & Berries", 350, protein = 15f, carbs = 60f, fat = 5f)
        )

        val report = DossierReportGenerator.generateWeeklyMarkdown(
            meals = meals,
            calorieTarget = 2000,
            complianceScore = 85,
            complianceStatus = ComplianceStatus.ACCEPTABLE,
            generatedTimestamp = 1_700_000_000_000L
        )

        assertTrue(report.contains("TOTAL 7-DAY INTAKE   : 1050 kcal across 3 meals"))
        assertTrue(report.contains("PROTEIN : 100g total"))
        assertTrue(report.contains("CARBS   : 75g total"))
        assertTrue(report.contains("FAT     : 22g total"))
        assertTrue(report.contains("LIQUIDS : 1 liquid events (33% of total entries)"))
        assertTrue(report.contains("STATUS VERDICT      : ACCEPTABLE (85/100)"))
    }

    @Test
    fun generateWeeklyMarkdownWithViolations() {
        val meals = listOf(
            sampleMeal(
                "Restricted Zone Pizza",
                900,
                isRestricted = true,
                isNightRefueling = false
            ),
            sampleMeal(
                "Late Night Donuts",
                600,
                isRestricted = false,
                isNightRefueling = true
            )
        )

        val report = DossierReportGenerator.generateWeeklyMarkdown(
            meals = meals,
            calorieTarget = 2000,
            complianceScore = 35,
            complianceStatus = ComplianceStatus.SUBVERSIVE,
            generatedTimestamp = 1_700_000_000_000L
        )

        assertTrue(report.contains("ZONE RESTRICTION INFRACTIONS : 1"))
        assertTrue(report.contains("NIGHT REFUELING VIOLATIONS   : 1"))
        assertTrue(report.contains("TOTAL FLAGGED ANOMALIES      : 2"))
        assertTrue(report.contains("Restricted Zone Pizza (900 kcal) => RESTRICTED ZONE"))
        assertTrue(report.contains("Late Night Donuts (600 kcal) => NIGHT REFUELING"))
        assertTrue(report.contains("WARNING: Unsanctioned nutritional deviations detected."))
    }
}
