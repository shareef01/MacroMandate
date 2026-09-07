package com.sharek.macromandate.util

import com.sharek.macromandate.model.MealEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URI

/**
 * Summary of a parsed JSON backup file for preview before committing to Room.
 */
data class BackupParseSummary(
    val version: Int,
    val exportedAt: Long?,
    val totalInArchive: Int,
    val validMeals: List<MealEntry>,
    val skippedCount: Int,
    val hasImages: Boolean
)

/**
 * Writes and reads meal history for export and restore.
 *
 * This handles **meal-record history**, not full database snapshots (API keys,
 * settings, and raw image binaries are intentionally excluded).
 */
object DossierExporter {

    const val BACKUP_VERSION = 1
    private const val MAX_BACKUP_CHARS = 10 * 1024 * 1024 // 10 MB
    private const val UNKNOWN_MEAL_NAME = "RESTORED MEAL"
    private const val EVIDENCE_DIR_NAME = "evidence"
    private const val MAX_URI_LENGTH = 512
    private const val MAX_LATITUDE = 90.0
    private const val MAX_LONGITUDE = 180.0
    private val FORMULA_TRIGGERS = charArrayOf('=', '+', '-', '@')

    // 2020-01-01 00:00:00 UTC. The app did not exist before this; any timestamp
    // earlier is corrupted data, not history.
    private const val EARLIEST_PLAUSIBLE_TIMESTAMP = 1577836800000L

    suspend fun generateCsv(meals: List<MealEntry>): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        // UTF-8 BOM so Excel opens accented characters without manual import steps.
        sb.append('\uFEFF')
        sb.append("id,timestamp,foodName,calories,proteinGrams,carbsGrams,fatGrams,isLiquid\r\n")

        meals.forEach { meal ->
            sb.append(csvCell(meal.id)).append(',')
            sb.append(meal.timestamp).append(',')
            sb.append(csvCell(meal.foodName)).append(',')
            sb.append(meal.calories).append(',')
            sb.append(meal.proteinGrams).append(',')
            sb.append(meal.carbsGrams).append(',')
            sb.append(meal.fatGrams).append(',')
            sb.append(meal.isLiquid).append("\r\n")
        }
        sb.toString()
    }

    /** Wraps a field in RFC-4180 quotes after neutralizing formula injection. */
    private fun csvCell(value: String): String = "\"${escapeCsvField(value)}\""

    suspend fun generateJson(meals: List<MealEntry>): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("version", BACKUP_VERSION)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("count", meals.size)

        val array = JSONArray()
        meals.forEach { meal ->
            val obj = JSONObject().apply {
                put("id", meal.id)
                put("timestamp", meal.timestamp)
                put("foodName", meal.foodName)
                put("calories", meal.calories)
                put("proteinGrams", meal.proteinGrams.toDouble())
                put("carbsGrams", meal.carbsGrams.toDouble())
                put("fatGrams", meal.fatGrams.toDouble())
                put("isLiquid", meal.isLiquid)
                if (meal.imageUri != null) put("imageUri", meal.imageUri)
                if (meal.latitude != null) put("latitude", meal.latitude)
                if (meal.longitude != null) put("longitude", meal.longitude)
                if (meal.assessment != null) put("assessment", meal.assessment)
                put("isRestricted", meal.isRestricted)
                put("isNightRefueling", meal.isNightRefueling)
            }
            array.put(obj)
        }
        root.put("meals", array)
        root.toString(2)
    }

    /** Why a restore could not proceed, in terms the user can act on. */
    sealed class RestoreError {
        object NotAnArchive : RestoreError()
        data class UnsupportedVersion(val found: Int) : RestoreError()
        object TooLarge : RestoreError()
    }

    /**
     * Reads a backup file and produces a structured [BackupParseSummary].
     *
     * Validates bounds, enforces deterministic legacy IDs for missing IDs,
     * and nullifies dead image paths that don't exist on disk if [fileVerifier] is provided.
     */
    suspend fun parseJsonBackupSummary(
        jsonString: String,
        fileVerifier: ((String) -> Boolean)? = null
    ): Result<BackupParseSummary> = withContext(Dispatchers.IO) {
        if (jsonString.length > MAX_BACKUP_CHARS) {
            return@withContext Result.failure(RestoreException(RestoreError.TooLarge))
        }

        val root = try {
            JSONObject(jsonString)
        } catch (e: Exception) {
            return@withContext Result.failure(RestoreException(RestoreError.NotAnArchive))
        }

        val version = root.optInt("version", -1)
        if (version <= 0) {
            return@withContext Result.failure(RestoreException(RestoreError.NotAnArchive))
        }
        if (version > BACKUP_VERSION) {
            return@withContext Result.failure(RestoreException(RestoreError.UnsupportedVersion(version)))
        }

        val array = root.optJSONArray("meals")
            ?: return@withContext Result.failure(RestoreException(RestoreError.NotAnArchive))

        val exportedAt = if (root.has("exportedAt")) root.optLong("exportedAt") else null
        val totalCount = array.length()
        val meals = ArrayList<MealEntry>(totalCount)
        val seenIds = HashSet<String>(totalCount)
        var skipped = 0
        var hasImages = false

        for (i in 0 until totalCount) {
            val obj = array.optJSONObject(i)
            if (obj == null) {
                skipped++
                continue
            }

            val declaredId = obj.optString("id").trim()
            // Deterministic legacy identity based on stable canonical fields
            val id = if (declaredId.isEmpty()) {
                val canonicalKey = "${obj.optLong("timestamp", 0L)}_${obj.optString("foodName", "").trim()}_${obj.optInt("calories", 0)}"
                "legacy_" + canonicalKey.hashCode().toUInt().toString(16)
            } else declaredId

            // Duplicate ID within archive: keep the first, drop duplicate deterministically
            if (!seenIds.add(id)) {
                skipped++
                continue
            }

            val rawImageUri = obj.optString("imageUri", "")
            val sanitizedImageUri = sanitizeImageUri(rawImageUri, fileVerifier)
            if (sanitizedImageUri != null) {
                hasImages = true
            }

            val protein = NutritionBounds.clampGrams(obj.optDouble("proteinGrams", 0.0).toFloat())
            val carbs = NutritionBounds.clampGrams(obj.optDouble("carbsGrams", 0.0).toFloat())
            val fat = NutritionBounds.clampGrams(obj.optDouble("fatGrams", 0.0).toFloat())

            meals.add(
                MealEntry(
                    id = id,
                    timestamp = clampTimestamp(obj.optLong("timestamp", System.currentTimeMillis())),
                    imageUri = sanitizedImageUri,
                    foodName = NutritionBounds.clampName(obj.optString("foodName", ""), UNKNOWN_MEAL_NAME),
                    calories = NutritionBounds.clampCalories(obj.optDouble("calories", 0.0)),
                    proteinGrams = protein,
                    carbsGrams = carbs,
                    fatGrams = fat,
                    isLiquid = obj.optBoolean("isLiquid", false),
                    latitude = readCoordinate(obj, "latitude", MAX_LATITUDE),
                    longitude = readCoordinate(obj, "longitude", MAX_LONGITUDE),
                    assessment = NutritionBounds.clampAssessment(obj.optString("assessment", "")),
                    isRestricted = obj.optBoolean("isRestricted", false),
                    isNightRefueling = obj.optBoolean("isNightRefueling", false)
                )
            )
        }

        Result.success(
            BackupParseSummary(
                version = version,
                exportedAt = exportedAt,
                totalInArchive = totalCount,
                validMeals = meals,
                skippedCount = skipped,
                hasImages = hasImages
            )
        )
    }

    /**
     * Legacy entry point returning just the meal entries. Preserves backward compatibility.
     */
    suspend fun parseJsonBackup(jsonString: String): Result<List<MealEntry>> =
        parseJsonBackupSummary(jsonString).map { it.validMeals }

    class RestoreException(val error: RestoreError) : Exception(error.toString())

    private fun clampTimestamp(value: Long): Long {
        val now = System.currentTimeMillis()
        return value.coerceIn(EARLIEST_PLAUSIBLE_TIMESTAMP, now)
    }

    private fun readCoordinate(obj: JSONObject, key: String, limit: Double): Double? {
        if (!obj.has(key) || obj.isNull(key)) return null
        val value = obj.optDouble(key, Double.NaN)
        if (value.isNaN() || value.isInfinite() || value < -limit || value > limit) return null
        return value
    }

    /**
     * Accepts only `file://` URIs under the app's own evidence directory name.
     * Anything else — a `content://` provider, an absolute path elsewhere, a
     * traversal — is dropped and the record restores without an image.
     *
     * If [fileVerifier] is provided, verifies that the file exists on the current device.
     */
    internal fun sanitizeImageUri(raw: String, fileVerifier: ((String) -> Boolean)? = null): String? {
        val value = raw.trim()
        if (value.isEmpty()) return null
        if (!value.startsWith("file:///")) return null
        if (value.contains("..")) return null
        if (!value.contains("/$EVIDENCE_DIR_NAME/")) return null
        val safeUri = value.take(MAX_URI_LENGTH)
        if (fileVerifier != null && !fileVerifier(safeUri)) {
            return null
        }
        return safeUri
    }

    /** Helper verifier checking actual file existence on disk */
    fun createFileExistenceVerifier(): (String) -> Boolean = { uriString ->
        try {
            val uri = URI(uriString)
            val file = File(uri.path)
            file.exists() && file.isFile
        } catch (e: Exception) {
            false
        }
    }

    internal fun escapeCsvField(field: String): String {
        val flattened = field.replace("\r", " ").replace("\n", " ").replace("\"", "\"\"")
        return if (flattened.isNotEmpty() && flattened[0] in FORMULA_TRIGGERS) {
            "'$flattened"
        } else {
            flattened
        }
    }
}
