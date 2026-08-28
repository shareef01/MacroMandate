package com.sharek.macromandate.viewmodel

import android.net.Uri
import com.sharek.macromandate.util.ParsedNutrition

/**
 * An analysis result that has been read but **not yet written to the log**.
 *
 * The capture flow used to insert the model's answer straight into Room and tell
 * the user afterwards, via a snackbar, what had been recorded on their behalf.
 * That made an estimate indistinguishable from something they entered: the only
 * way to find out what was saved was to open the record, and the only way to fix
 * it was to edit a row that already counted toward the day's totals.
 *
 * Holding the result here instead means the number is confirmed by a person
 * before it becomes part of their history.
 *
 * @property sourceImage the image as captured or picked. Persisted into the
 *   evidence store only if the user confirms, so a discarded analysis leaves
 *   nothing behind.
 * @property capturedAt fixed at analysis time so a meal reviewed slowly is still
 *   timestamped when it was photographed.
 */
data class PendingAnalysis(
    val sourceImage: Uri,
    val nutrition: ParsedNutrition,
    val capturedAt: Long,
    val latitude: Double? = null,
    val longitude: Double? = null
) {
    /**
     * Whether anything about this result warrants a visible caveat beyond the
     * standing "estimate" framing — the model contradicting itself, or the app
     * having filled in a number the model never gave.
     */
    val hasCaveat: Boolean
        get() = nutrition.caloriesDerivedFromMacros ||
            nutrition.caloriesContradictMacros ||
            nutrition.valuesClamped

    /** A short, non-alarming explanation of [hasCaveat], or null when there is none. */
    val caveat: String?
        get() = when {
            nutrition.caloriesContradictMacros ->
                "The calorie figure doesn't match the macros given. Worth checking."
            nutrition.caloriesDerivedFromMacros ->
                "No calorie figure was returned, so this is calculated from the macros."
            nutrition.valuesClamped ->
                "Some values were outside the range this app can store and were adjusted."
            else -> null
        }
}
