package com.sharek.macromandate.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
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
