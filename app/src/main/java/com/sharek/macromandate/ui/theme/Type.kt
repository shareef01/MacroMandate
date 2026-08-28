package com.sharek.macromandate.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Two faces, with a rule about which is which.
 *
 * The previous scale defined only 7 of the ~15 Material styles. The other eight
 * — including `bodyMedium`, `labelMedium` and `labelLarge`, which between them
 * carry the navigation labels, the status banner, the settings descriptions and
 * most dialog copy — silently fell through to Material's Roboto default. The app
 * was already mixed-typeface; it just wasn't on purpose.
 *
 * Making it deliberate:
 *
 * **[Terminal] (monospace)** — anything that reads as instrumentation. Numbers,
 * because a monospaced digit keeps columns of macros aligned down a list; status
 * lines; metadata; headings; the hero calorie figure.
 *
 * **[Prose] (the platform sans)** — sentences. Settings descriptions, dialog
 * body copy, disclosures, empty states, error messages. Monospace is a texture,
 * and reading a three-line explanation set in it is measurably slower; the
 * places where the app has something real to explain are exactly the places that
 * should not make the reader work.
 *
 * The identity survives because the terminal face still carries every element
 * the eye lands on first. It is the paragraphs that get a readable face — which
 * is the same trade a well-set instrument panel makes.
 */
private val Terminal = FontFamily.Monospace
private val Prose = FontFamily.Default

val Typography = Typography(

    // ---- Hero data --------------------------------------------------------
    // The one number the dashboard exists to show.
    displayLarge = TextStyle(
        fontFamily = Terminal,
        fontWeight = FontWeight.Black,
        fontSize = 52.sp,
        lineHeight = 60.sp,
        letterSpacing = (-1.5).sp
    ),
    displayMedium = TextStyle(
        fontFamily = Terminal,
        fontWeight = FontWeight.Black,
        fontSize = 44.sp,
        lineHeight = 52.sp,
        letterSpacing = (-1).sp
    ),
    displaySmall = TextStyle(
        fontFamily = Terminal,
        fontWeight = FontWeight.Black,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-1).sp
    ),

    // ---- Headings ---------------------------------------------------------
    headlineLarge = TextStyle(
        fontFamily = Terminal,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 38.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = Terminal,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 34.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = Terminal,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),

    // ---- Section titles ---------------------------------------------------
    titleLarge = TextStyle(
        fontFamily = Terminal,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = Terminal,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = Terminal,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),

    // ---- Prose ------------------------------------------------------------
    // Sentences the user has to actually read.
    bodyLarge = TextStyle(
        fontFamily = Prose,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = Prose,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = Prose,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.3.sp
    ),

    // ---- System labels ----------------------------------------------------
    // Short, often all-caps, always instrumentation.
    labelLarge = TextStyle(
        fontFamily = Terminal,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.5.sp
    ),
    labelMedium = TextStyle(
        fontFamily = Terminal,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    // 12sp, not 11. This is the most-used style in the app (30 call sites) and
    // it was the smallest text on screen while carrying the macro readout on the
    // Today card. One point back costs nothing and is a floor worth holding.
    labelSmall = TextStyle(
        fontFamily = Terminal,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)
