package com.sharek.macromandate.domain

import com.sharek.macromandate.model.MealEntry
import com.sharek.macromandate.util.EvidenceDeleteAllResult
import kotlinx.coroutines.CancellationException

data class DeleteAllResult(
    val databaseCleared: Boolean,
    val evidenceCleared: Boolean,
    val failedEvidenceCount: Int = 0
) {
    val isCompleteSuccess: Boolean get() = databaseCleared && evidenceCleared
}

data class DeleteMealResult(
    val databaseDeleted: Boolean,
    val evidenceDeleted: Boolean
) {
    val isCompleteSuccess: Boolean get() = databaseDeleted && evidenceDeleted
}

/** Coordinates independent database and evidence deletion outcomes. */
class DataDeletionService(
    private val getMealById: suspend (String) -> MealEntry?,
    private val deleteMealRecord: suspend (String) -> Unit,
    private val deleteEvidence: (String?) -> Boolean,
    private val clearDatabaseAndActivity: suspend () -> Unit,
    private val clearAllEvidence: () -> EvidenceDeleteAllResult
) {
    suspend fun deleteMeal(id: String): DeleteMealResult {
        val meal = try { getMealById(id) } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return DeleteMealResult(false, false)
        } ?: return DeleteMealResult(true, true)
        try { deleteMealRecord(id) } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return DeleteMealResult(false, false)
        }
        return DeleteMealResult(true, deleteEvidence(meal.imageUri))
    }

    suspend fun deleteEverything(): DeleteAllResult {
        val databaseCleared = try {
            clearDatabaseAndActivity()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
        val evidence = try {
            clearAllEvidence()
        } catch (_: Exception) {
            EvidenceDeleteAllResult(0, 1)
        }
        return DeleteAllResult(databaseCleared, evidence.complete, evidence.failedCount)
    }
}
