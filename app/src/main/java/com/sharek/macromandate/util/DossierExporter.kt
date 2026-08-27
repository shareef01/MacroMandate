package com.sharek.macromandate.util

import com.sharek.macromandate.model.MealEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

object DossierExporter {

    private const val BACKUP_VERSION = 1
    // Excel/Sheets evaluate a cell as a formula when it opens with one of these.
    private val FORMULA_TRIGGERS = charArrayOf('=', '+', '-', '@', '\t')

    suspend fun generateCsv(meals: List<MealEntry>): String = withContext(Dispatchers.IO) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val sb = StringBuilder()
        sb.append("ID,Timestamp,FoodName,Calories,Protein,Carbs,Fat,IsLiquid\n")

        meals.forEach { meal ->
            val timestamp = dateFormat.format(Date(meal.timestamp))
            sb.append("${meal.id},")
            sb.append("$timestamp,")
            sb.append("\"${escapeCsvField(meal.foodName)}\",")
            sb.append("${meal.calories},")
            sb.append("${meal.proteinGrams},")
            sb.append("${meal.carbsGrams},")
            sb.append("${meal.fatGrams},")
            sb.append("${meal.isLiquid}\n")
        }
        sb.toString()
    }

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

    suspend fun parseJsonBackup(jsonString: String): List<MealEntry>? = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonString)
            val version = root.optInt("version", -1)
            if (version <= 0) return@withContext null

            val array = root.optJSONArray("meals") ?: return@withContext null
            val meals = mutableListOf<MealEntry>()

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.optString("id").ifBlank { UUID.randomUUID().toString() }
                val timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                val foodName = obj.optString("foodName", "UNKNOWN REFUELING")
                val calories = obj.optInt("calories", 0)
                val protein = obj.optDouble("proteinGrams", 0.0).toFloat()
                val carbs = obj.optDouble("carbsGrams", 0.0).toFloat()
                val fat = obj.optDouble("fatGrams", 0.0).toFloat()
                val isLiquid = obj.optBoolean("isLiquid", false)
                val imageUri = if (obj.has("imageUri")) obj.getString("imageUri") else null
                val latitude = if (obj.has("latitude") && !obj.isNull("latitude")) obj.getDouble("latitude") else null
                val longitude = if (obj.has("longitude") && !obj.isNull("longitude")) obj.getDouble("longitude") else null
                val assessment = if (obj.has("assessment") && !obj.isNull("assessment")) obj.getString("assessment") else null
                val isRestricted = obj.optBoolean("isRestricted", false)
                val isNightRefueling = obj.optBoolean("isNightRefueling", false)

                meals.add(
                    MealEntry(
                        id = id,
                        timestamp = timestamp,
                        imageUri = imageUri,
                        foodName = foodName,
                        calories = calories,
                        proteinGrams = protein,
                        carbsGrams = carbs,
                        fatGrams = fat,
                        isLiquid = isLiquid,
                        latitude = latitude,
                        longitude = longitude,
                        assessment = assessment,
                        isRestricted = isRestricted,
                        isNightRefueling = isNightRefueling
                    )
                )
            }
            meals
        } catch (e: Exception) {
            null
        }
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
