package com.sharek.macromandate.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Subtle, optional CRT terminal filter.
 *
 * Kept intentionally low-intensity so it never obscures content, degrades WCAG AA
 * text contrast, or interferes with accessibility. Can be disabled in Settings.
 */
fun Modifier.crtFilter(enabled: Boolean = true): Modifier = if (!enabled) this else this.drawWithCache {
    val scanlineHeight = 1.5.dp.toPx()
    val scanlineStep = 4.dp.toPx()
    val vignetteColors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.22f))

    onDrawWithContent {
        drawContent()

        var y = 0f
        while (y < size.height) {
            drawLine(
                color = Color.Black.copy(alpha = 0.05f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = scanlineHeight,
                blendMode = BlendMode.Multiply
            )
            y += scanlineStep
        }

        drawRect(
            brush = Brush.radialGradient(
                colors = vignetteColors,
                center = Offset(size.width / 2f, size.height / 2f),
                radius = maxOf(size.width, size.height) * 0.7f
            ),
            blendMode = BlendMode.Multiply
        )
    }
}
