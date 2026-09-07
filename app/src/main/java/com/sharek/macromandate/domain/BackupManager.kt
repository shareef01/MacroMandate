package com.sharek.macromandate.domain

import android.content.Context
import android.net.Uri
import android.util.Log
import com.sharek.macromandate.data.repository.MealRepository
import com.sharek.macromandate.model.MealEntry
import com.sharek.macromandate.util.BackupParseSummary
import com.sharek.macromandate.util.DossierExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Encapsulates meal record backup, export, validation preview, and merge restore.
 */
class BackupManager(
    private val context: Context,
    private val mealRepository: MealRepository
) {
    private companion object {
        const val TAG = "BackupManager"
    }

    suspend fun exportCsv(meals: List<MealEntry>, targetUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val csv = DossierExporter.generateCsv(meals)
            context.contentResolver.openOutputStream(targetUri)?.use { output ->
                output.write(csv.toByteArray(Charsets.UTF_8))
                true
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "CSV export failed", e)
            false
        }
    }

    suspend fun exportJsonBackup(meals: List<MealEntry>, targetUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = DossierExporter.generateJson(meals)
            context.contentResolver.openOutputStream(targetUri)?.use { output ->
                output.write(json.toByteArray(Charsets.UTF_8))
                true
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "JSON backup export failed", e)
            false
        }
    }

    suspend fun previewBackup(sourceUri: Uri): Result<BackupParseSummary> = withContext(Dispatchers.IO) {
        try {
            val jsonString = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                input.bufferedReader(Charsets.UTF_8).readText()
            } ?: return@withContext Result.failure(Exception("Failed to read backup file."))

            DossierExporter.parseJsonBackupSummary(
                jsonString = jsonString,
                fileVerifier = DossierExporter.createFileExistenceVerifier()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Backup preview failed", e)
            Result.failure(e)
        }
    }

    suspend fun restoreMeals(meals: List<MealEntry>): Result<Int> = withContext(Dispatchers.IO) {
        try {
            if (meals.isNotEmpty()) {
                mealRepository.insertMeals(meals)
            }
            Result.success(meals.size)
        } catch (e: Exception) {
            Log.e(TAG, "Meal restore failed", e)
            Result.failure(e)
        }
    }
}
