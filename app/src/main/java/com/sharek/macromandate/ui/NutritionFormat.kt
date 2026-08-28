package com.sharek.macromandate.ui

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * How nutrition numbers are rendered, in one place.
 *
 * The app previously called `.toInt()` on macro floats at every display site,
 * which truncated rather than rounded: 12.7 g of protein read as "12 g" on the
 * meal card, the detail screen and the totals row, so the visible figures sat
 * systematically below the stored ones. Rounding here — and rounding the same
 * way everywhere — keeps what the user sees consistent with what was saved.
 */

/** A macro amount with its unit, e.g. "12.7 g" or "40 g". */
fun formatGrams(value: Float): String = "${formatGramsValue(value)} g"

/**
 * A macro amount without its unit. Whole numbers lose the decimal point so a
 * row of totals stays scannable; fractions keep one place so a correction the
 * user typed is not silently discarded on the way back out.
 */
fun formatGramsValue(value: Float): String {
    if (!value.isFinite()) return "0"
    val rounded = (value * 10f).roundToInt() / 10f
    return if (abs(rounded - rounded.roundToInt()) < 0.05f) {
        rounded.roundToInt().toString()
    } else {
        String.format(Locale.getDefault(), "%.1f", rounded)
    }
}

/**
 * A spoken description of a macro trio for TalkBack.
 *
 * The visual row reads "P: 40g  C: 12g  F: 8g", which a screen reader announces
 * as "P colon forty g, C colon twelve g, F colon eight g". Sighted users have
 * the colour coding and column position to decode the abbreviations; a screen
 * reader user has neither, so they get the words.
 */
fun macroContentDescription(protein: Float, carbs: Float, fat: Float): String =
    "Protein ${formatGramsValue(protein)} grams. " +
        "Carbohydrates ${formatGramsValue(carbs)} grams. " +
        "Fat ${formatGramsValue(fat)} grams."

/**
 * Accepts digits and at most one decimal separator, taking a comma as one too.
 *
 * The entry dialogs filtered input with `it.filter { ch -> ch.isDigit() || ch == '.' }`.
 * On a keyboard laid out for a comma-decimal locale the separator key produces
 * a comma, which that filter dropped silently: the user typed "12,5" and the
 * field showed "125" — a tenfold error, entered without any indication that
 * something had been discarded. It also permitted "1.2.3", which then failed to
 * parse and fell back to zero.
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

/** Reads a sanitized decimal field back to grams; unparsable input means zero. */
fun parseGrams(value: String): Float = value.toFloatOrNull() ?: 0f

/**
 * Reads a calorie field, rejecting values that overflow [Int].
 *
 * `toIntOrNull()` returns null past Int.MAX_VALUE, and the dialogs turned that
 * null into 0 — so typing fifteen digits silently logged a zero-calorie meal.
 * Callers get null here and can keep the previous value instead.
 */
fun parseCalories(value: String): Int? = value.toLongOrNull()?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt()

/** Long enough for "1234.5"; short enough that overflow is unreachable. */
private const val MAX_DECIMAL_INPUT_LENGTH = 7
