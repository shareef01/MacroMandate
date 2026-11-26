package com.sharek.macromandate.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/**
 * Centralized semantic tokens for the MacroMandate terminal design system.
 *
 * Spacing scale: 4, 8, 12, 16, 24, 32 dp.
 * Minimum touch target: 48 dp.
 */
@Immutable
data class TerminalTokens(
    val background: Color,
    val surface: Color,
    val elevatedSurface: Color,
    val border: Color,
    val divider: Color,
    val primaryAccent: Color,
    val secondaryAccent: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val disabledText: Color,
    val disabledSurface: Color,
    val warning: Color,
    val error: Color,
    val proteinColor: Color = NutritionColors.Protein,
    val carbsColor: Color = NutritionColors.Carbs,
    val fatColor: Color = NutritionColors.Fat,
    val spacingXs: Dp = 4.dp,
    val spacingSm: Dp = 8.dp,
    val spacingMd: Dp = 12.dp,
    val spacingLg: Dp = 16.dp,
    val spacingXl: Dp = 24.dp,
    val spacingXxl: Dp = 32.dp,
    val cornerRadiusSm: Dp = 4.dp,
    val cornerRadiusMd: Dp = 6.dp,
    val minTouchTarget: Dp = 48.dp,
    val maxContentWidth: Dp = 640.dp,
    val cardShape: Shape = RoundedCornerShape(4.dp),
    val controlShape: Shape = RoundedCornerShape(4.dp)
)

fun terminalTokensFor(theme: TerminalTheme): TerminalTokens = TerminalTokens(
    background = theme.backgroundColor,
    surface = theme.surfaceColor,
    elevatedSurface = theme.elevatedSurfaceColor,
    border = theme.borderColor,
    divider = theme.borderColor.copy(alpha = 0.7f),
    primaryAccent = theme.primaryColor,
    secondaryAccent = theme.secondaryColor,
    primaryText = theme.textPrimaryColor,
    secondaryText = theme.textSecondaryColor,
    disabledText = theme.textSecondaryColor.copy(alpha = 0.45f),
    disabledSurface = theme.surfaceColor.copy(alpha = 0.5f),
    warning = theme.warningColor,
    error = SubversiveRed
)

val LocalTerminalTokens = staticCompositionLocalOf {
    terminalTokensFor(TerminalTheme.CYBER_CYAN)
}

object TerminalDesign {
    val tokens: TerminalTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalTerminalTokens.current
}

val TerminalShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(6.dp),
    extraLarge = RoundedCornerShape(8.dp)
)

fun terminalColorScheme(theme: TerminalTheme): ColorScheme = darkColorScheme(
    primary = theme.primaryColor,
    onPrimary = theme.backgroundColor,
    primaryContainer = theme.elevatedSurfaceColor,
    onPrimaryContainer = theme.textPrimaryColor,
    secondary = theme.secondaryColor,
    onSecondary = theme.backgroundColor,
    secondaryContainer = theme.containerColor,
    onSecondaryContainer = theme.textPrimaryColor,
    tertiary = theme.warningColor,
    onTertiary = theme.backgroundColor,
    background = theme.backgroundColor,
    onBackground = theme.textPrimaryColor,
    surface = theme.surfaceColor,
    onSurface = theme.textPrimaryColor,
    surfaceVariant = theme.elevatedSurfaceColor,
    onSurfaceVariant = theme.textSecondaryColor,
    outline = theme.borderColor,
    outlineVariant = theme.borderColor.copy(alpha = 0.6f),
    error = SubversiveRed,
    onError = theme.backgroundColor,
    errorContainer = Color(0xFF38131A),
    onErrorContainer = Color(0xFFFFDAD6)
)

@Composable
fun MacroMandateTheme(
    terminalTheme: TerminalTheme = TerminalTheme.CYBER_CYAN,
    content: @Composable () -> Unit
) {
    val colorScheme = terminalColorScheme(terminalTheme)
    val tokens = terminalTokensFor(terminalTheme)
    val view = LocalView.current

    val activity = view.context as? Activity
    if (!view.isInEditMode && activity != null) {
        SideEffect {
            val controller = WindowCompat.getInsetsController(activity.window, view)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }
    }

    CompositionLocalProvider(LocalTerminalTokens provides tokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = TerminalShapes,
            content = content
        )
    }
}
