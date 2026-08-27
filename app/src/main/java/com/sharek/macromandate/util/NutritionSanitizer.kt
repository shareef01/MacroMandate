package com.sharek.macromandate.util

import org.json.JSONObject
import kotlin.math.roundToInt

data class ParsedNutrition(
    val foodName: String,
    val calories: Int,
    val proteinGrams: Float,
    val carbsGrams: Float,
    val fatGrams: Float,
    val isLiquid: Boolean,
    val assessment: String,
    val confidence: String? = null
)

object NutritionSanitizer {

    fun parseAndSanitize(cleanJson: String): ParsedNutrition {
        val jsonObject = JSONObject(cleanJson)

        val rawName = jsonObject.optString("foodName", "").trim()
        val foodName = when {
            rawName.isNotBlank() -> rawName
            jsonObject.has("name") -> jsonObject.optString("name", "Unidentified Item").trim()
            jsonObject.has("item") -> jsonObject.optString("item", "Unidentified Item").trim()
            else -> "Unidentified Item"
        }

        val rawProtein = parseSafeFloat(jsonObject, "proteinGrams", "protein")
        val rawCarbs = parseSafeFloat(jsonObject, "carbsGrams", "carbs", "carbohydrates")
        val rawFat = parseSafeFloat(jsonObject, "fatGrams", "fat")

        val protein = rawProtein.coerceAtLeast(0f)
        val carbs = rawCarbs.coerceAtLeast(0f)
        val fat = rawFat.coerceAtLeast(0f)

        var rawCalories = parseSafeInt(jsonObject, "calories", "kcal", "energy")
        // If calories was omitted or 0, but positive macros were provided, calculate sanity estimation
        if (rawCalories <= 0 && (protein > 0f || carbs > 0f || fat > 0f)) {
            rawCalories = ((protein * 4f) + (carbs * 4f) + (fat * 9f)).roundToInt()
        }
        val calories = rawCalories.coerceAtLeast(0)

        val isLiquid = jsonObject.optBoolean("isLiquid", false) ||
                jsonObject.optString("type", "").equals("liquid", ignoreCase = true) ||
                jsonObject.optString("beverage", "").equals("true", ignoreCase = true)

        val rawAssessment = jsonObject.optString("assessment", "").trim()
        val assessment = if (rawAssessment.isNotBlank()) rawAssessment else "NOMINAL REFUELING REGISTERED."
        val confidence = jsonObject.optString("confidence", "").ifBlank { null }

        return ParsedNutrition(
            foodName = foodName,
            calories = calories,
            proteinGrams = protein,
            carbsGrams = carbs,
            fatGrams = fat,
            isLiquid = isLiquid,
            assessment = assessment,
            confidence = confidence
        )
    }

    private fun parseSafeFloat(json: JSONObject, vararg keys: String): Float {
        for (key in keys) {
            if (json.has(key) && !json.isNull(key)) {
                val optDouble = json.optDouble(key, Double.NaN)
                if (!optDouble.isNaN() && !optDouble.isInfinite()) {
                    return optDouble.toFloat()
                }
                val optStr = json.optString(key, "")
                val parsed = optStr.replace("g", "").replace("G", "").trim().toFloatOrNull()
                if (parsed != null && !parsed.isNaN() && !parsed.isInfinite()) {
                    return parsed
                }
            }
        }
        return 0f
    }

    private fun parseSafeInt(json: JSONObject, vararg keys: String): Int {
        for (key in keys) {
            if (json.has(key) && !json.isNull(key)) {
                val optInt = json.optInt(key, -1)
                if (optInt >= 0) return optInt

                val optDouble = json.optDouble(key, -1.0)
                if (optDouble >= 0 && !optDouble.isNaN() && !optDouble.isInfinite()) {
                    return optDouble.toInt()
                }
                val optStr = json.optString(key, "")
                val parsed = optStr.replace("kcal", "").replace("calories", "").replace("cal", "").trim().toIntOrNull()
                if (parsed != null && parsed >= 0) {
                    return parsed
                }
            }
        }
        return 0
    }
}
