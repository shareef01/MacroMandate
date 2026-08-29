package com.sharek.macromandate.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

fun Modifier.crtFilter(enabled: Boolean = true): Modifier = if (!enabled) this else this.drawWithCache {
    val scanlineHeight = 2.dp.toPx()
    val vignetteColors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
    
    onDrawWithContent {
        drawContent()
        
        // Scanlines
        var y = 0f
        while (y < size.height) {
            drawLine(
                color = Color.Black.copy(alpha = 0.15f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = scanlineHeight,
                blendMode = BlendMode.Multiply
            )
            y += scanlineHeight * 2.5f
        }
        
        // Vignette
        drawRect(
            brush = Brush.radialGradient(
                colors = vignetteColors,
                center = Offset(size.width / 2f, size.height / 2f),
                radius = size.width * 0.75f
            ),
            blendMode = BlendMode.Multiply
        )
        
        // Slight screen glare overlay
        drawRect(
            color = Color(0xFFFFFFFF).copy(alpha = 0.03f),
            blendMode = BlendMode.Screen
        )
    }
}
