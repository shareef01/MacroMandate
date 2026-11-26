package com.sharek.macromandate.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Semantic palette tokens for each terminal theme.
 *
 * The directional reference palette (#080E0B background, #111D16 surface,
 * #203128 elevated surface, #304038 border, #79EFA9 primary accent,
 * #E7F1EA primary text, #9BB5A5 secondary text, #DFBD73 warning) defines
 * the contrast and surface-depth hierarchy across all supported themes.
 */
enum class TerminalTheme(
    val id: String,
    val displayName: String,
    val tagline: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val backgroundColor: Color,
    val surfaceColor: Color,
    val elevatedSurfaceColor: Color,
    val borderColor: Color,
    val containerColor: Color,
    val textPrimaryColor: Color,
    val textSecondaryColor: Color,
    val warningColor: Color = Color(0xFFDFBD73)
) {
    CYBER_CYAN(
        id = "cyber_cyan",
        displayName = "CYBER CYAN",
        tagline = "Optical Uplink",
        primaryColor = Color(0xFF63E6BE),
        secondaryColor = Color(0xFF38D9A9),
        backgroundColor = Color(0xFF080E0B),
        surfaceColor = Color(0xFF111D16),
        elevatedSurfaceColor = Color(0xFF203128),
        borderColor = Color(0xFF304038),
        containerColor = Color(0xFF1A2B22),
        textPrimaryColor = Color(0xFFE7F1EA),
        textSecondaryColor = Color(0xFF9BB5A5),
        warningColor = Color(0xFFDFBD73)
    ),
    MATRIX_GREEN(
        id = "matrix_green",
        displayName = "PHOSPHOR GREEN",
        tagline = "Tactical Field Terminal",
        primaryColor = Color(0xFF79EFA9),
        secondaryColor = Color(0xFF52D68A),
        backgroundColor = Color(0xFF080E0B),
        surfaceColor = Color(0xFF111D16),
        elevatedSurfaceColor = Color(0xFF203128),
        borderColor = Color(0xFF304038),
        containerColor = Color(0xFF192A20),
        textPrimaryColor = Color(0xFFE7F1EA),
        textSecondaryColor = Color(0xFF9BB5A5),
        warningColor = Color(0xFFDFBD73)
    ),
    AMBER_CRT(
        id = "amber_crt",
        displayName = "AMBER CRT",
        tagline = "Hardened Phosphor Console",
        primaryColor = Color(0xFFF0B849),
        secondaryColor = Color(0xFFD99B26),
        backgroundColor = Color(0xFF0D0A06),
        surfaceColor = Color(0xFF1A140C),
        elevatedSurfaceColor = Color(0xFF2B2114),
        borderColor = Color(0xFF423422),
        containerColor = Color(0xFF261D10),
        textPrimaryColor = Color(0xFFF3ECE2),
        textSecondaryColor = Color(0xFFB8A68E),
        warningColor = Color(0xFFDFBD73)
    ),
    STARK_MONO(
        id = "stark_mono",
        displayName = "STARK MONO",
        tagline = "High-Contrast Console",
        primaryColor = Color(0xFFDCE5DF),
        secondaryColor = Color(0xFFA3B3A9),
        backgroundColor = Color(0xFF090B0A),
        surfaceColor = Color(0xFF131715),
        elevatedSurfaceColor = Color(0xFF212824),
        borderColor = Color(0xFF343E38),
        containerColor = Color(0xFF1C221E),
        textPrimaryColor = Color(0xFFECF2EE),
        textSecondaryColor = Color(0xFFA0AFA6),
        warningColor = Color(0xFFDFBD73)
    );

    companion object {
        fun fromId(id: String?): TerminalTheme =
            entries.firstOrNull { it.id == id } ?: CYBER_CYAN
    }
}

// Reference Palette Constants
val TerminalBlack = Color(0xFF080E0B)
val ColdSteel = Color(0xFF9BB5A5)
val StarkWhite = Color(0xFFE7F1EA)
val DeepCharcoal = Color(0xFF111D16)
val SurfaceGray = Color(0xFF203128)
val TerminalBorder = Color(0xFF304038)

// Status Colors
val ExemplaryBlue = Color(0xFF79EFA9)
val WarningYellow = Color(0xFFDFBD73)
val SubversiveRed = Color(0xFFFF5C6C)

/**
 * Semantic Nutrition Tokens (Consistent & Accessible Across Themes).
 * Distinct from theme primary colors so macro color-coding remains unambiguous.
 */
object NutritionColors {
    val Calories = Color(0xFF79EFA9)     // Phosphor Mint
    val Protein = Color(0xFF4ADE80)      // Emerald Green
    val Carbs = Color(0xFF58C4F6)        // Ice Cyan-Blue
    val Fat = Color(0xFFDFBD73)          // Warm Amber Gold
    val OverTarget = SubversiveRed       // Alert Coral-Red
}
