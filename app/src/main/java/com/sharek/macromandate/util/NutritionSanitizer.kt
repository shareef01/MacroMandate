package com.sharek.macromandate.util

import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.IOException
import java.io.StringReader
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
        require(cleanJson.length <= 16_384) { "Nutrition result is too large." }
        val jsonObject = try {
            JsonReader(StringReader(cleanJson)).use { reader ->
                reader.strictness = Strictness.STRICT
                reader.nestingLimit = 32
                val parsed = JsonParser.parseReader(reader)
                require(parsed.isJsonObject && reader.peek() == JsonToken.END_DOCUMENT) {
                    "Expected one nutrition object."
                }
                parsed.asJsonObject
            }
        } catch (e: JsonParseException) {
            throw IllegalArgumentException("Invalid nutrition JSON.", e)
        } catch (e: IOException) {
            throw IllegalArgumentException("Invalid nutrition JSON.", e)
        }

        val foodName = sequenceOf("foodName", "name", "item")
            .mapNotNull { jsonObject.text(it)?.trim()?.takeIf(String::isNotEmpty) }
            .firstOrNull()?.take(120) ?: throw IllegalArgumentException("Missing food name.")
        val rawProtein = readNumber(jsonObject, GRAMS_SUFFIX, "proteinGrams", "protein")
        val rawCarbs = readNumber(jsonObject, GRAMS_SUFFIX, "carbsGrams", "carbs", "carbohydrates")
        val rawFat = readNumber(jsonObject, GRAMS_SUFFIX, "fatGrams", "fat")
        val rawCalories = readNumber(jsonObject, CALORIE_SUFFIX, "calories", "kcal", "energy")
        require(rawCalories != null || rawProtein != null || rawCarbs != null || rawFat != null) {
            "No usable nutrition values."
        }
        val protein = (rawProtein ?: 0.0).coerceAtMost(1_000.0).toFloat()
        val carbs = (rawCarbs ?: 0.0).coerceAtMost(1_000.0).toFloat()
        val fat = (rawFat ?: 0.0).coerceAtMost(1_000.0).toFloat()
        val calories = (rawCalories ?: (protein * 4.0 + carbs * 4.0 + fat * 9.0))
            .coerceAtMost(20_000.0).roundToInt()

        val isLiquid = sequenceOf("isLiquid", "beverage").any {
            val value = jsonObject.get(it)
            value != null && value.isJsonPrimitive && value.asString.equals("true", ignoreCase = true)
        } || jsonObject.text("type").equals("liquid", ignoreCase = true)

        val rawAssessment = jsonObject.text("assessment").orEmpty().trim().take(500)
        val assessment = if (rawAssessment.isNotBlank()) rawAssessment else "NOMINAL REFUELING REGISTERED."
        val confidence = jsonObject.text("confidence")?.trim()?.take(80)?.ifBlank { null }

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

    private val GRAMS_SUFFIX = Regex("\\s*g$", RegexOption.IGNORE_CASE)
    private val CALORIE_SUFFIX = Regex("\\s*(kcal|calories|cal)$", RegexOption.IGNORE_CASE)

    private fun JsonObject.text(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    private fun readNumber(json: JsonObject, suffix: Regex, vararg keys: String): Double? {
        for (key in keys) {
            val raw = json.get(key)?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive ?: continue
            if (!raw.isNumber && !raw.isString) continue
            val value = raw.asString.trim().replace(suffix, "").toDoubleOrNull()
            if (value != null && value.isFinite() && value >= 0.0) return value
        }
        return null
    }
}
