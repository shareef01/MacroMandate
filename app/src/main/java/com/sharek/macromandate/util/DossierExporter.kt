package com.sharek.macromandate.util

import com.sharek.macromandate.model.MealEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

object DossierExporter {

    const val BACKUP_VERSION = 1

    // Excel/Sheets evaluate a cell as a formula when it opens with one of these.
    private val FORMULA_TRIGGERS = charArrayOf('=', '+', '-', '@', '\t')

    /**
     * Restore reads the whole file into memory, so it needs a ceiling. ~16M chars
     * is far beyond any real export (a 10-year log is a few MB) while staying well
     * inside the heap a mid-range device gives a single app.
     */
    internal const val MAX_BACKUP_CHARS = 16 * 1024 * 1024

    private const val UNKNOWN_MEAL_NAME = "Untitled meal"
    private const val MAX_URI_LENGTH = 512
    private const val EVIDENCE_DIR_NAME = "evidence"
    private const val MAX_LATITUDE = 90.0
    private const val MAX_LONGITUDE = 180.0

    /** 2000-01-01. Anything older is a corrupt or fabricated timestamp, not history. */
    private const val EARLIEST_PLAUSIBLE_TIMESTAMP = 946_684_800_000L

    /** Escaped, not literal: a raw BOM mid-file is itself a lint error. */
    private const val UTF8_BOM = "\uFEFF"

    /**
     * A spreadsheet-facing export. Deliberately excludes coordinates, the stored
     * image path and the model's assessment prose: a CSV is the artefact people
     * mail to themselves and open on a shared machine, so it carries the
     * nutrition record and nothing that reveals where they were.
     *
     * Use the JSON backup for a complete, restorable archive.
     */
    suspend fun generateCsv(meals: List<MealEntry>): String = withContext(Dispatchers.IO) {
        // ISO-8601 with a UTC offset, so a reader can place the row in time
        // instead of guessing which zone the exporting device was in.
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
        val sb = StringBuilder()
        // Excel on Windows assumes the system codepage without a BOM and mangles
        // any non-ASCII dish name.
        sb.append(UTF8_BOM)
        sb.append("ID,Timestamp,FoodName,Calories,ProteinGrams,CarbsGrams,FatGrams,IsLiquid\r\n")

        meals.forEach { meal ->
            sb.append(csvCell(meal.id)).append(',')
            sb.append(csvCell(dateFormat.format(Date(meal.timestamp)))).append(',')
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
     * Reads a backup file. Returns the meals on success, or a [RestoreError].
     *
     * This is a hostile-input boundary: the file is arbitrary bytes chosen by
     * whoever hands it to the picker, so nothing in it is trusted. Every numeric
     * field is clamped through [NutritionBounds] — the same gate the entry
     * dialogs use — because a restore that skipped validation was a way to write
     * values into Room that no screen would ever accept.
     */
    suspend fun parseJsonBackup(jsonString: String): Result<List<MealEntry>> = withContext(Dispatchers.IO) {
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
        // A newer file may carry fields this build cannot represent. Silently
        // dropping them would look like a successful restore that lost data.
        if (version > BACKUP_VERSION) {
            return@withContext Result.failure(RestoreException(RestoreError.UnsupportedVersion(version)))
        }

        val array = root.optJSONArray("meals")
            ?: return@withContext Result.failure(RestoreException(RestoreError.NotAnArchive))

        val meals = ArrayList<MealEntry>(array.length())
        val seenIds = HashSet<String>(array.length())

        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue

            // A file may repeat an id; Room would REPLACE, so the last one would
            // silently win. Keep the first and drop the rest, deterministically.
            val declaredId = obj.optString("id").trim()
            val id = if (declaredId.isEmpty()) UUID.randomUUID().toString() else declaredId
            if (!seenIds.add(id)) continue

            val protein = NutritionBounds.clampGrams(obj.optDouble("proteinGrams", 0.0).toFloat())
            val carbs = NutritionBounds.clampGrams(obj.optDouble("carbsGrams", 0.0).toFloat())
            val fat = NutritionBounds.clampGrams(obj.optDouble("fatGrams", 0.0).toFloat())

            meals.add(
                MealEntry(
                    id = id,
                    timestamp = clampTimestamp(obj.optLong("timestamp", System.currentTimeMillis())),
                    // Only paths this app owns are honoured. A backup could otherwise
                    // point a record at any URI on the device and have the detail
                    // screen render it — and have deletion act on it.
                    imageUri = sanitizeImageUri(obj.optString("imageUri", "")),
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
        Result.success(meals)
    }

    class RestoreException(val error: RestoreError) : Exception(error.toString())

    /**
     * Keeps a restored timestamp inside a range the rest of the app can reason
     * about. A far-future timestamp is the worst case: "today" is an open-ended
     * `timestamp >= startOfDay` query, so one bad row would be counted in every
     * daily total from now on.
     */
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
     */
    private fun sanitizeImageUri(raw: String): String? {
        val value = raw.trim()
        if (value.isEmpty()) return null
        if (!value.startsWith("file:///")) return null
        if (value.contains("..")) return null
        if (!value.contains("/$EVIDENCE_DIR_NAME/")) return null
        return value.take(MAX_URI_LENGTH)
    }

    internal fun escapeCsvField(field: String): String {
        // LLM-generated food names can contain quotes and line breaks; quotes are
        // doubled (RFC-4180) and newlines stripped so each record stays on one row.
        val flattened = field.replace("\r", " ").replace("\n", " ").replace("\"", "\"\"")
        // foodName comes back from the model, so it is attacker-influencable via the
        // photo. Neutralize spreadsheet formula injection with a leading apostrophe.
        return if (flattened.isNotEmpty() && flattened[0] in FORMULA_TRIGGERS) {
            "'$flattened"
        } else {
            flattened
        }
    }
}
