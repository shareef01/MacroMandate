package com.sharek.macromandate.util

import kotlin.math.roundToInt

/**
 * The single definition of "structurally plausible" nutrition data.
 *
 * Every ingestion boundary funnels through here — model output, manual entry,
 * the edit dialog, and JSON restore — so a value that the UI would reject cannot
 * reach Room by taking a different route. Restoring a hand-edited backup used to
 * bypass every check the dialogs applied.
 *
 * These are **parser bounds, not dietary advice**. They exist to keep a corrupt
 * or hostile number from poisoning aggregates, charts and the widget; they are
 * set far outside any real meal so they never second-guess what someone ate.
 * Nothing here should ever be surfaced as a health limit.
 */
object NutritionBounds {

    /** Roughly ten times the largest competitive-eating record. Not a diet rule. */
    const val MAX_CALORIES = 20_000

    /** A meal cannot plausibly carry more than two kilograms of one macronutrient. */
    const val MAX_MACRO_GRAMS = 2_000f

    /** Long enough for any real dish; short enough that a hostile blob cannot bloat the DB. */
    const val MAX_NAME_LENGTH = 120

    /** Model assessments are prose; cap them so one response cannot balloon a row. */
    const val MAX_ASSESSMENT_LENGTH = 500

    /** kcal per gram, used only for the fallback estimate when no calories are given. */
    private const val KCAL_PER_GRAM_PROTEIN = 4f
    private const val KCAL_PER_GRAM_CARB = 4f
    private const val KCAL_PER_GRAM_FAT = 9f

    /**
     * Clamps calories into [0, MAX_CALORIES]. Non-finite input collapses to 0
     * rather than propagating NaN into every downstream sum.
     */
    fun clampCalories(value: Int): Int = value.coerceIn(0, MAX_CALORIES)

    fun clampCalories(value: Double): Int = when {
        value.isNaN() -> 0
        value <= 0.0 -> 0
        value >= MAX_CALORIES.toDouble() -> MAX_CALORIES
        else -> value.roundToInt()
    }

    /** Clamps a macronutrient gram value into [0, MAX_MACRO_GRAMS]; NaN/Inf become 0. */
    fun clampGrams(value: Float): Float = when {
        value.isNaN() -> 0f
        value <= 0f -> 0f
        value >= MAX_MACRO_GRAMS -> MAX_MACRO_GRAMS
        else -> value
    }

    /** Trims and truncates a user- or model-supplied name, falling back to [fallback]. */
    fun clampName(value: String?, fallback: String): String {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isEmpty()) return fallback
        return trimmed.take(MAX_NAME_LENGTH)
    }

    /** Truncates free-text model prose. Returns null for blank input. */
    fun clampAssessment(value: String?): String? {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return trimmed.take(MAX_ASSESSMENT_LENGTH)
    }

    /** The Atwater estimate for a set of macros, used only when calories are absent. */
    fun caloriesFromMacros(proteinGrams: Float, carbsGrams: Float, fatGrams: Float): Int =
        clampCalories(
            (proteinGrams * KCAL_PER_GRAM_PROTEIN +
                carbsGrams * KCAL_PER_GRAM_CARB +
                fatGrams * KCAL_PER_GRAM_FAT).toDouble()
        )

    /**
     * True when supplied calories are irreconcilable with the supplied macros.
     *
     * The macros alone already account for [caloriesFromMacros]; a stated calorie
     * count far below that, or many times above it, means the model contradicted
     * itself. We surface that as an uncertainty flag rather than silently
     * rewriting the number, because we cannot know which half is wrong.
     *
     * The band is deliberately wide — real dishes vary with fibre, alcohol and
     * rounding — so only genuine self-contradiction trips it.
     */
    fun caloriesContradictMacros(calories: Int, proteinGrams: Float, carbsGrams: Float, fatGrams: Float): Boolean {
        val fromMacros = caloriesFromMacros(proteinGrams, carbsGrams, fatGrams)
        // Nothing to compare against when no macros were reported at all.
        if (fromMacros < 50) return false
        if (calories <= 0) return false
        return calories < fromMacros * 0.5 || calories > fromMacros * 2.5
    }
}
