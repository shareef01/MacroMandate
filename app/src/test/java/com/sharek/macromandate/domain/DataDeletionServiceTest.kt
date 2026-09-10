package com.sharek.macromandate.domain

import com.sharek.macromandate.model.MealEntry
import com.sharek.macromandate.util.EvidenceDeleteAllResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataDeletionServiceTest {
    private fun meal(uri: String? = "file:///evidence/a.jpg") = MealEntry(
        id = "meal", timestamp = 1L, imageUri = uri, foodName = "Meal", calories = 1,
        proteinGrams = 0f, carbsGrams = 0f, fatGrams = 0f, isLiquid = false,
        latitude = null, longitude = null, assessment = null,
        isRestricted = false, isNightRefueling = false
    )

    @Test
    fun deleteAllData_filesFail_doesNotReportCompleteSuccess() = runBlocking {
        val service = DataDeletionService({ null }, {}, { true }, {}, {
            EvidenceDeleteAllResult(1, 2)
        })
        val result = service.deleteEverything()
        assertTrue(result.databaseCleared)
        assertFalse(result.isCompleteSuccess)
        assertTrue(result.failedEvidenceCount == 2)
    }

    @Test
    fun deleteAllData_success_leavesNoUserAuditRecords() = runBlocking {
        val audits = mutableListOf("record")
        val service = DataDeletionService({ null }, {}, { true }, { audits.clear() }, {
            EvidenceDeleteAllResult(2, 0)
        })
        assertTrue(service.deleteEverything().isCompleteSuccess)
        assertTrue(audits.isEmpty())
    }

    @Test
    fun deleteMeal_withoutActiveMealFlow_stillFindsEvidence() = runBlocking {
        var stored: MealEntry? = meal()
        var deletedUri: String? = null
        val service = DataDeletionService(
            getMealById = { stored },
            deleteMealRecord = { stored = null },
            deleteEvidence = { deletedUri = it; true },
            clearDatabaseAndActivity = {},
            clearAllEvidence = { EvidenceDeleteAllResult(0, 0) }
        )
        assertTrue(service.deleteMeal("meal").isCompleteSuccess)
        assertNull(stored)
        assertTrue(deletedUri!!.endsWith("a.jpg"))
    }
}
