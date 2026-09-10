package com.sharek.macromandate.domain

import android.content.Context
import android.net.Uri
import android.util.Log
import com.sharek.macromandate.data.repository.MealRepository
import com.sharek.macromandate.model.MealEntry
import com.sharek.macromandate.util.BackupParseSummary
import com.sharek.macromandate.util.DossierExporter
import com.sharek.macromandate.util.EvidenceStore
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun writeNonEmptyUtf8(text: String, openOutput: () -> OutputStream?): Boolean {
    val bytes = text.toByteArray(Charsets.UTF_8)
    if (bytes.isEmpty()) return false
    val output = openOutput() ?: return false
    output.use {
        it.write(bytes)
        it.flush()
    }
    return true
}

internal fun readUtf8Bounded(input: InputStream, maxBytes: Int): String {
    val output = ByteArrayOutputStream(minOf(8192, maxBytes))
    val buffer = ByteArray(8192)
    var total = 0
    while (true) {
        val allowedRead = minOf(buffer.size, maxBytes - total + 1)
        val count = input.read(buffer, 0, allowedRead)
        if (count < 0) break
        total += count
        if (total > maxBytes) {
            throw DossierExporter.RestoreException(DossierExporter.RestoreError.TooLarge)
        }
        output.write(buffer, 0, count)
    }
    return output.toString(Charsets.UTF_8.name())
}

/**
 * Encapsulates meal record backup, export, validation preview, and merge restore.
 */
class BackupManager(
    private val context: Context,
    private val mealRepository: MealRepository
) {
    private companion object {
        const val TAG = "BackupManager"
        const val MAX_BACKUP_BYTES = 10 * 1024 * 1024
    }

    data class ExportResult(val succeeded: Boolean, val recordCount: Int = 0)

    suspend fun exportCsv(targetUri: Uri): ExportResult = withContext(Dispatchers.IO) {
        try {
            val meals = mealRepository.getAllMealsSnapshot()
            val csv = DossierExporter.generateCsv(meals)
            ExportResult(writeNonEmpty(targetUri, csv), meals.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "CSV export failed", e)
            ExportResult(false)
        }
    }

    suspend fun exportJsonBackup(targetUri: Uri): ExportResult = withContext(Dispatchers.IO) {
        try {
            val meals = mealRepository.getAllMealsSnapshot()
            val json = DossierExporter.generateJson(meals)
            ExportResult(writeNonEmpty(targetUri, json), meals.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "JSON backup export failed", e)
            ExportResult(false)
        }
    }

    suspend fun exportText(targetUri: Uri, text: String): Boolean = withContext(Dispatchers.IO) {
        try { writeNonEmpty(targetUri, text) } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Text export failed", e)
            false
        }
    }

    private fun writeNonEmpty(targetUri: Uri, text: String): Boolean {
        return writeNonEmptyUtf8(text) { context.contentResolver.openOutputStream(targetUri) }
    }

    suspend fun previewBackup(sourceUri: Uri): Result<BackupParseSummary> = withContext(Dispatchers.IO) {
        try {
            val jsonString = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                readUtf8Bounded(input, MAX_BACKUP_BYTES)
            } ?: return@withContext Result.failure(Exception("Failed to read backup file."))

            DossierExporter.parseJsonBackupSummary(
                jsonString = jsonString,
                fileVerifier = { raw ->
                    runCatching {
                        EvidenceStore.isStored(context, Uri.parse(raw), requireExists = true)
                    }.getOrDefault(false)
                }
            )
        } catch (e: CancellationException) {
            throw e
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Meal restore failed", e)
            Result.failure(e)
        }
    }
}
