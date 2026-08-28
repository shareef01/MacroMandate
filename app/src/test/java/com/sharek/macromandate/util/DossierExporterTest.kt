package com.sharek.macromandate.util

import com.sharek.macromandate.model.MealEntry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DossierExporterTest {

    // Distinct ids. Every fixture meal previously shared id "abc123", so the
    // round-trip test asserted that two records survived a restore while Room's
    // REPLACE conflict strategy would in fact have collapsed them into one.
    private fun meal(foodName: String, calories: Int = 100, id: String = foodName.filter { it.isLetterOrDigit() }) = MealEntry(
        id = id,
        timestamp = 1_700_000_000_000L,
        imageUri = null,
        foodName = foodName,
        calories = calories,
        proteinGrams = 10f,
        carbsGrams = 20f,
        fatGrams = 5f,
        isLiquid = false
    )

    @Test
    fun csvHasHeaderRow() = runBlocking {
        val csv = DossierExporter.generateCsv(emptyList())
        // Leading UTF-8 BOM so Excel on Windows does not mangle non-ASCII names.
        assertTrue(csv.startsWith("﻿ID,Timestamp,FoodName,Calories,ProteinGrams,CarbsGrams,FatGrams,IsLiquid"))
    }

    @Test
    fun csvEscapesEmbeddedQuotesInFoodName() = runBlocking {
        val csv = DossierExporter.generateCsv(listOf(meal("Burger \"Double\"")))
        // RFC-4180: embedded quotes are doubled inside a quoted field.
        assertTrue(csv.contains("\"Burger \"\"Double\"\"\""))
    }

    @Test
    fun csvStripsLineBreaksFromFoodName() = runBlocking {
        val csv = DossierExporter.generateCsv(listOf(meal("Shake\r\nwith\nextra")))
        // Newlines must not split a record into multiple rows.
        assertTrue(csv.contains("\"Shake  with extra\""))
        assertTrue(csv.lines().count { it.isNotBlank() } == 2) // header + 1 row
    }

    @Test
    fun csvIncludesAllRows() = runBlocking {
        val csv = DossierExporter.generateCsv(listOf(meal("Salad", 250), meal("Soup", 180)))
        assertTrue(csv.lines().count { it.isNotBlank() } == 3) // header + 2 rows
    }

    @Test
    fun formulaTriggersArePrefixed() {
        // foodName is model-generated, so a leading formula character must not reach
        // a spreadsheet unescaped.
        listOf("=1+1", "+SUM(A1)", "-2", "@import", "\tcmd").forEach { field ->
            assertTrue(
                "expected '$field' to be neutralized",
                DossierExporter.escapeCsvField(field).startsWith("'")
            )
        }
    }

    @Test
    fun ordinaryNamesAreNotPrefixed() {
        assertEquals("Grilled Salmon", DossierExporter.escapeCsvField("Grilled Salmon"))
    }

    @Test
    fun formulaEscapingSurvivesGeneratedCsv() = runBlocking {
        val csv = DossierExporter.generateCsv(listOf(meal("=cmd|'/c calc'!A1")))
        assertTrue(csv.contains("\"'=cmd"))
    }

    @Test
    fun emptyFoodNameIsHandled() {
        assertEquals("", DossierExporter.escapeCsvField(""))
    }

    @Test
    fun jsonBackupIncludesSchemaMetadataAndMeals() = runBlocking {
        val meals = listOf(
            meal("Ribeye Steak", 750),
            meal("Protein Shake", 200).copy(isLiquid = true, proteinGrams = 40f)
        )
        val json = DossierExporter.generateJson(meals)
        assertTrue(json.contains("\"version\": 1"))
        assertTrue(json.contains("\"count\": 2"))
        assertTrue(json.contains("Ribeye Steak"))
        assertTrue(json.contains("Protein Shake"))
    }

    @Test
    fun jsonBackupRoundTripRestoresIdenticalMeals() = runBlocking {
        val originalMeals = listOf(
            meal("Oatmeal", 350).copy(
                latitude = 37.7749,
                longitude = -122.4194,
                assessment = "NOMINAL MORNING FUEL",
                isRestricted = false,
                isNightRefueling = false
            ),
            meal("Midnight Snack", 420).copy(
                isLiquid = false,
                isRestricted = true,
                isNightRefueling = true
            )
        )

        val json = DossierExporter.generateJson(originalMeals)
        val parsedMeals = DossierExporter.parseJsonBackup(json).getOrThrow()

        assertEquals(2, parsedMeals.size)

        val first = parsedMeals[0]
        assertEquals("Oatmeal", first.foodName)
        assertEquals(350, first.calories)
        assertEquals(37.7749, first.latitude!!, 0.001)
        assertEquals(-122.4194, first.longitude!!, 0.001)
        assertEquals("NOMINAL MORNING FUEL", first.assessment)

        val second = parsedMeals[1]
        assertEquals("Midnight Snack", second.foodName)
        assertTrue(second.isRestricted)
        assertTrue(second.isNightRefueling)
    }

    @Test
    fun parseJsonBackupRejectsMalformedInput() = runBlocking {
        val result = DossierExporter.parseJsonBackup("INVALID JSON NOT A STRING")
        assertTrue(result.isFailure)
    }

    @Test
    fun parseJsonBackupRejectsMissingVersion() = runBlocking {
        val result = DossierExporter.parseJsonBackup("{\"meals\":[]}")
        assertTrue(result.isFailure)
    }
}
