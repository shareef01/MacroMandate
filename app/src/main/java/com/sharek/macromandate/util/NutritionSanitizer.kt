package com.sharek.macromandate.util

import org.json.JSONObject

/**
 * The result of reading one model response.
 *
 * [caloriesDerivedFromMacros] and [caloriesContradictMacros] are carried out of
 * the parser instead of being resolved here: the UI has to be able to say which
 * numbers the model actually stated and which the app inferred. Collapsing that
 * distinction is how an estimate starts reading like a measurement.
 */
data class ParsedNutrition(
    val foodName: String,
    val calories: Int,
    val proteinGrams: Float,
    val carbsGrams: Float,
    val fatGrams: Float,
    val isLiquid: Boolean,
    val assessment: String?,
    val caloriesDerivedFromMacros: Boolean = false,
    val caloriesContradictMacros: Boolean = false,
    val valuesClamped: Boolean = false
)

object NutritionSanitizer {

    private const val DEFAULT_NAME = "Unidentified item"

    /**
     * Reads one nutrition object out of an untrusted model response.
     *
     * Every field is optional and every type is assumed wrong until proven
     * otherwise: providers return numbers as strings, macros as `"12 g"`, and
     * occasionally arrays or nulls where an object was asked for. Anything
     * unreadable degrades to a neutral default rather than throwing, so a
     * malformed field cannot take down the capture flow.
     *
     * @throws org.json.JSONException if [cleanJson] is not a JSON object at all.
     */
    fun parseAndSanitize(cleanJson: String): ParsedNutrition {
        val jsonObject = JSONObject(cleanJson)

        val foodName = NutritionBounds.clampName(
            firstNonBlankString(jsonObject, "foodName", "name", "item"),
            DEFAULT_NAME
        )

        val rawProtein = parseSafeFloat(jsonObject, "proteinGrams", "protein")
        val rawCarbs = parseSafeFloat(jsonObject, "carbsGrams", "carbs", "carbohydrates")
        val rawFat = parseSafeFloat(jsonObject, "fatGrams", "fat")

        val protein = NutritionBounds.clampGrams(rawProtein)
        val carbs = NutritionBounds.clampGrams(rawCarbs)
        val fat = NutritionBounds.clampGrams(rawFat)

        // Raw, unclamped: the clamp happens once below so that clamping stays
        // observable in [ParsedNutrition.valuesClamped].
        val rawCalories = parseSafeCalories(jsonObject, "calories", "kcal", "energy")

        // Only fill in calories when the model gave us none. When it did give a
        // number we keep it even if it disagrees with the macros, and flag the
        // disagreement instead — we have no basis for deciding which is right.
        val derived = rawCalories <= 0.0 && (protein > 0f || carbs > 0f || fat > 0f)
        val calories = if (derived) {
            NutritionBounds.caloriesFromMacros(protein, carbs, fat)
        } else {
            NutritionBounds.clampCalories(rawCalories)
        }

        val clamped = rawProtein != protein ||
            rawCarbs != carbs ||
            rawFat != fat ||
            (!derived && rawCalories > NutritionBounds.MAX_CALORIES.toDouble())

        val isLiquid = jsonObject.optBoolean("isLiquid", false) ||
            jsonObject.optString("type", "").equals("liquid", ignoreCase = true) ||
            jsonObject.optString("beverage", "").equals("true", ignoreCase = true)

        return ParsedNutrition(
            foodName = foodName,
            calories = calories,
            proteinGrams = protein,
            carbsGrams = carbs,
            fatGrams = fat,
            isLiquid = isLiquid,
            assessment = NutritionBounds.clampAssessment(jsonObject.optString("assessment", "")),
            caloriesDerivedFromMacros = derived,
            caloriesContradictMacros = NutritionBounds.caloriesContradictMacros(calories, protein, carbs, fat),
            valuesClamped = clamped
        )
    }

    private fun firstNonBlankString(json: JSONObject, vararg keys: String): String? {
        for (key in keys) {
            if (!json.has(key) || json.isNull(key)) continue
            val value = json.optString(key, "").trim()
            if (value.isNotEmpty()) return value
        }
        return null
    }

    /**
     * Reads a gram value that may arrive as a number, a numeric string, or a
     * string carrying its unit (`"12g"`, `"12 G"`). Values that are absent,
     * non-finite, or structurally wrong (arrays, objects) yield 0.
     */
    private fun parseSafeFloat(json: JSONObject, vararg keys: String): Float {
        for (key in keys) {
            if (!json.has(key) || json.isNull(key)) continue

            val asDouble = json.optDouble(key, Double.NaN)
            if (!asDouble.isNaN() && !asDouble.isInfinite()) return asDouble.toFloat()

            val parsed = json.optString(key, "")
                .replace("g", "", ignoreCase = true)
                .trim()
                .toFloatOrNull()
            if (parsed != null && parsed.isFinite()) return parsed
        }
        return 0f
    }

    /**
     * Reads a calorie value that may arrive as a number, a numeric string, or a
     * string carrying its unit (`"420 kcal"`). Returns the value **unclamped**
     * so the caller can tell an out-of-range figure from an in-range one.
     * Negative and unreadable values yield 0 and fall through to the
     * macro-derived estimate.
     */
    private fun parseSafeCalories(json: JSONObject, vararg keys: String): Double {
        for (key in keys) {
            if (!json.has(key) || json.isNull(key)) continue

            val asDouble = json.optDouble(key, Double.NaN)
            if (asDouble.isFinite() && asDouble >= 0.0) return asDouble
            // Infinity is out of range rather than unreadable; report it as such
            // so it is clamped and flagged instead of silently becoming zero.
            if (asDouble == Double.POSITIVE_INFINITY) return Double.MAX_VALUE

            val parsed = json.optString(key, "")
                .replace("kcal", "", ignoreCase = true)
                .replace("calories", "", ignoreCase = true)
                .replace("cal", "", ignoreCase = true)
                .trim()
                .toDoubleOrNull()
            if (parsed != null && parsed.isFinite() && parsed >= 0.0) return parsed
        }
        return 0.0
    }
}
