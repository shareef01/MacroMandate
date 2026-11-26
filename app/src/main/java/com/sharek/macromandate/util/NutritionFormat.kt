package com.sharek.macromandate.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Centralized nutrition formatting, parsing, validation, and date-window rules.
 *
 * Fractional nutrient values are preserved internally as [Float] and rounded
 * intentionally only at presentation boundaries (1 decimal place when fractional,
 * integer when whole).
 */
object NutritionFormat {

    /** Maximum reasonable single-meal calorie limit (storage sanity bound, not dietary advice). */
    const val MAX_MEAL_CALORIES = 20_000

    /** Maximum reasonable single-meal macronutrient limit in grams. */
    const val MAX_MEAL_MACRO_GRAMS = 2_000f

    /** Daily calorie goal sanity bounds. */
    const val MIN_CALORIE_TARGET = 500
    const val MAX_CALORIE_TARGET = 15_000
    const val DEFAULT_CALORIE_TARGET = 2_500

    /** Maximum characters accepted in a decimal macro input field. */
    private const val MAX_DECIMAL_INPUT_LENGTH = 7

    /** Formats a gram quantity with unit, e.g. "12.5 g" or "40 g". */
    fun formatGrams(value: Float, locale: Locale = Locale.getDefault()): String =
        "${formatGramsValue(value, locale)} g"

    /** Formats a gram quantity with compact unit, e.g. "12.5g" or "40g". */
    fun formatGramsCompact(value: Float, locale: Locale = Locale.getDefault()): String =
        "${formatGramsValue(value, locale)}g"

    /**
     * Formats a gram quantity without unit.
     * Whole numbers omit the decimal point; fractional values retain 1 decimal place.
     */
    fun formatGramsValue(value: Float, locale: Locale = Locale.getDefault()): String {
        if (!value.isFinite() || value <= 0f) return "0"
        val rounded = (value * 10f).roundToInt() / 10f
        return if (abs(rounded - rounded.roundToInt()) < 0.05f) {
            rounded.roundToInt().toString()
        } else {
            val symbols = DecimalFormatSymbols.getInstance(locale)
            DecimalFormat("0.0", symbols).format(rounded.toDouble())
        }
    }

    /** Formats a Double gram sum without premature truncation. */
    fun formatGramsSum(values: Iterable<Float>, locale: Locale = Locale.getDefault()): String {
        val sum = values.sumOf { if (it.isFinite() && it > 0f) it.toDouble() else 0.0 }.toFloat()
        return formatGramsValue(sum, locale)
    }

    /** Spoken description of a macronutrient trio for screen readers (TalkBack). */
    fun macroContentDescription(
        protein: Float,
        carbs: Float,
        fat: Float,
        locale: Locale = Locale.getDefault()
    ): String =
        "Protein ${formatGramsValue(protein, locale)} grams, " +
            "Carbohydrates ${formatGramsValue(carbs, locale)} grams, " +
            "Fat ${formatGramsValue(fat, locale)} grams."

    /**
     * Sanitizes a user-typed decimal string:
     * - Accepts digits and at most one decimal separator (`.` or `,`).
     * - Normalizes `,` to `.` so comma-decimal locales never lose their decimal point.
     */
    fun sanitizeDecimalInput(raw: String): String {
        val builder = StringBuilder()
        var separatorSeen = false
        for (ch in raw) {
            when {
                ch.isDigit() -> builder.append(ch)
                (ch == '.' || ch == ',') && !separatorSeen -> {
                    separatorSeen = true
                    builder.append('.')
                }
            }
        }
        return builder.toString().take(MAX_DECIMAL_INPUT_LENGTH)
    }

    /** Sanitizes an integer input string (digits only, up to [maxDigits]). */
    fun sanitizeIntegerInput(raw: String, maxDigits: Int = 6): String =
        raw.filter { it.isDigit() }.take(maxDigits)

    /**
     * Parses a gram input string.
     * Blank input is treated as 0f (macros are optional); non-blank invalid/out-of-range returns null.
     */
    fun parseGramsOrNull(raw: String): Float? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return 0f
        val normalized = trimmed.replace(',', '.')
        val value = normalized.toFloatOrNull() ?: return null
        if (!value.isFinite() || value < 0f || value > MAX_MEAL_MACRO_GRAMS) return null
        return value
    }

    /** Parses a gram input string, defaulting to 0f on blank/unparsable input. */
    fun parseGrams(raw: String): Float = parseGramsOrNull(raw) ?: 0f

    /**
     * Parses a calorie input string.
     * Requires explicit digits in `0..MAX_MEAL_CALORIES`. Blank or overflow returns null.
     */
    fun parseCaloriesOrNull(raw: String): Int? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val value = trimmed.toLongOrNull() ?: return null
        if (value < 0L || value > MAX_MEAL_CALORIES.toLong()) return null
        return value.toInt()
    }

    /** Calculates estimated calories from macronutrients using standard Atwater factors (4/4/9). */
    fun estimateCaloriesFromMacros(proteinGrams: Float, carbsGrams: Float, fatGrams: Float): Int {
        val p = if (proteinGrams.isFinite() && proteinGrams > 0f) proteinGrams.toDouble() else 0.0
        val c = if (carbsGrams.isFinite() && carbsGrams > 0f) carbsGrams.toDouble() else 0.0
        val f = if (fatGrams.isFinite() && fatGrams > 0f) fatGrams.toDouble() else 0.0
        return (p * 4.0 + c * 4.0 + f * 9.0).roundToInt().coerceIn(0, MAX_MEAL_CALORIES)
    }

    /** Validates a meal entry form before enabling Save. */
    fun isMealFormValid(
        foodName: String,
        caloriesStr: String,
        proteinStr: String = "",
        carbsStr: String = "",
        fatStr: String = ""
    ): Boolean {
        if (foodName.isBlank()) return false
        val cal = parseCaloriesOrNull(caloriesStr) ?: return false
        val p = parseGramsOrNull(proteinStr) ?: return false
        val c = parseGramsOrNull(carbsStr) ?: return false
        val f = parseGramsOrNull(fatStr) ?: return false
        return cal >= 0 && p >= 0f && c >= 0f && f >= 0f
    }

    /** Formats a timestamp as a readable local date and time with timezone, e.g. "2026-11-25 14:30 CET". */
    fun formatLocalTimestamp(
        timestampMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault()
    ): String {
        val zdt = Instant.ofEpochMilli(timestampMillis).atZone(zoneId)
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z", locale)
        return zdt.format(formatter)
    }

    /** Formats a timestamp as a short time of day, e.g. "14:30". */
    fun formatLocalTime(
        timestampMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault()
    ): String {
        val zdt = Instant.ofEpochMilli(timestampMillis).atZone(zoneId)
        val formatter = DateTimeFormatter.ofPattern("HH:mm", locale)
        return zdt.format(formatter)
    }

    /** Formats a LocalDate as a section header, e.g. "TODAY · WED, 25 NOV" or "TUE, 24 NOV 2026". */
    fun formatDateHeader(
        date: LocalDate,
        today: LocalDate = LocalDate.now(),
        locale: Locale = Locale.getDefault()
    ): String {
        val pattern = if (date.year == today.year) "EEE, d MMM" else "EEE, d MMM yyyy"
        val formatted = date.format(DateTimeFormatter.ofPattern(pattern, locale)).uppercase(locale)
        return when (date) {
            today -> "TODAY · $formatted"
            today.minusDays(1) -> "YESTERDAY · $formatted"
            else -> formatted
        }
    }

    /** Computes the half-open epoch millisecond window `[startInclusive, endExclusive)` for a [LocalDate]. */
    fun dayWindowMillis(date: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> {
        val start = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return start to end
    }
}
