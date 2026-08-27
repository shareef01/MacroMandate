package com.sharek.macromandate.ui.theme

import android.app.Activity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

fun terminalColorScheme(theme: TerminalTheme): ColorScheme = darkColorScheme(
    primary = theme.primaryColor,
    onPrimary = TerminalBlack,
    primaryContainer = theme.containerColor,
    onPrimaryContainer = theme.primaryColor,
    secondary = theme.secondaryColor,
    onSecondary = TerminalBlack,
    background = TerminalBlack,
    onBackground = theme.primaryColor,
    surface = theme.surfaceColor,
    onSurface = StarkWhite,
    surfaceVariant = SurfaceGray,
    onSurfaceVariant = theme.secondaryColor,
    error = SubversiveRed,
    onError = TerminalBlack,
    errorContainer = SubversiveRed,
    onErrorContainer = StarkWhite
)

@Composable
fun MacroMandateTheme(
    terminalTheme: TerminalTheme = TerminalTheme.CYBER_CYAN,
    content: @Composable () -> Unit
) {
    val colorScheme = terminalColorScheme(terminalTheme)
    val view = LocalView.current
    
    val activity = view.context as? Activity
    if (!view.isInEditMode && activity != null) {
        SideEffect {
            // statusBarColor is a no-op from API 35 on (edge-to-edge is enforced and
            // the bar is always transparent), so only icon appearance is set here.
            WindowCompat.getInsetsController(activity.window, view)
                .isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
