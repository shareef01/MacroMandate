package com.sharek.macromandate.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val BrutalistColorScheme = darkColorScheme(
    primary = StarkWhite,
    onPrimary = TerminalBlack,
    primaryContainer = ColdSteel,
    onPrimaryContainer = TerminalBlack,
    secondary = ColdSteel,
    onSecondary = TerminalBlack,
    background = TerminalBlack,
    onBackground = StarkWhite,
    surface = DeepCharcoal,
    onSurface = StarkWhite,
    surfaceVariant = SurfaceGray,
    onSurfaceVariant = ColdSteel,
    error = SubversiveRed,
    onError = TerminalBlack,
    errorContainer = SubversiveRed,
    onErrorContainer = StarkWhite
)

@Composable
fun MacroMandateTheme(
    content: @Composable () -> Unit
) {
    // Forcing Dark Theme for the Command Terminal aesthetic
    val colorScheme = BrutalistColorScheme
    val view = LocalView.current
    
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
