package com.sharek.macromandate.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MealEntryTest {

    @Test
    fun mealEntryDefaultValues() {
        val entry = MealEntry(
            id = "test-123",
            timestamp = 1_700_000_000_000L,
            imageUri = null,
            foodName = "Standard Ration",
            calories = 500,
            proteinGrams = 35f,
            carbsGrams = 50f,
            fatGrams = 15f,
            isLiquid = false
        )

        assertEquals("test-123", entry.id)
        assertEquals(1_700_000_000_000L, entry.timestamp)
        assertNull(entry.imageUri)
        assertNull(entry.latitude)
        assertNull(entry.longitude)
        assertNull(entry.assessment)
        assertFalse(entry.isRestricted)
        assertFalse(entry.isNightRefueling)
    }

    @Test
    fun mealEntryCustomTelemetryFields() {
        val entry = MealEntry(
            id = "test-456",
            timestamp = 1_700_000_000_000L,
            imageUri = "content://evidence/photo.jpg",
            foodName = "Night Fuel",
            calories = 650,
            proteinGrams = 45f,
            carbsGrams = 60f,
            fatGrams = 20f,
            isLiquid = true,
            latitude = 52.5200,
            longitude = 13.4050,
            assessment = "SUBVERSIVE CALORIE SPIKE",
            isRestricted = true,
            isNightRefueling = true
        )

        assertEquals("content://evidence/photo.jpg", entry.imageUri)
        assertTrue(entry.isLiquid)
        assertEquals(52.5200, entry.latitude!!, 0.0001)
        assertEquals(13.4050, entry.longitude!!, 0.0001)
        assertEquals("SUBVERSIVE CALORIE SPIKE", entry.assessment)
        assertTrue(entry.isRestricted)
        assertTrue(entry.isNightRefueling)
    }
}
