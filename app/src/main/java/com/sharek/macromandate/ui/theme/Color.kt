package com.sharek.macromandate.ui.theme

import androidx.compose.ui.graphics.Color

enum class TerminalTheme(
    val id: String,
    val displayName: String,
    val tagline: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val surfaceColor: Color,
    val containerColor: Color
) {
    CYBER_CYAN(
        id = "cyber_cyan",
        displayName = "CYBER CYAN",
        tagline = "Optical Uplink",
        primaryColor = Color(0xFF00E5FF),
        secondaryColor = Color(0xFF00B0FF),
        surfaceColor = Color(0xFF08131C),
        containerColor = Color(0xFF003844)
    ),
    MATRIX_GREEN(
        id = "matrix_green",
        displayName = "PHOSPHOR GREEN",
        tagline = "Subversive Mainframe",
        primaryColor = Color(0xFF00FF66),
        secondaryColor = Color(0xFF00E676),
        surfaceColor = Color(0xFF06140A),
        containerColor = Color(0xFF003814)
    ),
    AMBER_CRT(
        id = "amber_crt",
        displayName = "AMBER CRT",
        tagline = "Hardened Terminal",
        primaryColor = Color(0xFFFFB300),
        secondaryColor = Color(0xFFFF8F00),
        surfaceColor = Color(0xFF171004),
        containerColor = Color(0xFF382300)
    ),
    STARK_MONO(
        id = "stark_mono",
        displayName = "STARK MONO",
        tagline = "Bureaucratic Console",
        primaryColor = Color(0xFFE0E0E0),
        secondaryColor = Color(0xFF90A4AE),
        surfaceColor = Color(0xFF121212),
        containerColor = Color(0xFF263238)
    );

    companion object {
        fun fromId(id: String?): TerminalTheme =
            entries.firstOrNull { it.id == id } ?: CYBER_CYAN
    }
}

// Brutalist Shared Colors
val TerminalBlack = Color(0xFF000000)
val ColdSteel = Color(0xFF90A4AE)
val StarkWhite = Color(0xFFE0E0E0)
val DeepCharcoal = Color(0xFF121212)
val SurfaceGray = Color(0xFF1E1E1E)

// Mandate Status Colors (Highly Saturated)
val ExemplaryBlue = Color(0xFF2979FF)
val WarningYellow = Color(0xFFFFEA00)
val SubversiveRed = Color(0xFFFF1744)

// Semantic Nutrition Tokens (Consistent Across Themes)
object NutritionColors {
    val Protein = Color(0xFF00FF66)      // Phosphor Green
    val Carbs = Color(0xFF00E5FF)        // Cyber Cyan / Ice Blue
    val Fat = Color(0xFFFFD600)          // Amber Gold
    val OverTarget = SubversiveRed       // Alert Crimson
}
