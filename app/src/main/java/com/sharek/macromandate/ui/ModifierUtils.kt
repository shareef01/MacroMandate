package com.sharek.macromandate.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Four corner brackets, drawn behind the content they frame.
 *
 * The tactical framing device the app leans on. Cheap — eight solid rects — and
 * it reads as instrumentation rather than decoration because it only ever
 * appears around things that carry data.
 */
fun Modifier.hudFraming(
    color: Color,
    length: Dp = 20.dp,
    thickness: Dp = 2.dp
): Modifier = this.drawBehind {
    val len = length.toPx()
    val thick = thickness.toPx()
    val width = size.width
    val height = size.height

    // Top Left
    drawRect(color, topLeft = Offset(0f, 0f), size = Size(len, thick))
    drawRect(color, topLeft = Offset(0f, 0f), size = Size(thick, len))

    // Top Right
    drawRect(color, topLeft = Offset(width - len, 0f), size = Size(len, thick))
    drawRect(color, topLeft = Offset(width - thick, 0f), size = Size(thick, len))

    // Bottom Left
    drawRect(color, topLeft = Offset(0f, height - thick), size = Size(len, thick))
    drawRect(color, topLeft = Offset(0f, height - len), size = Size(thick, len))

    // Bottom Right
    drawRect(color, topLeft = Offset(width - len, height - thick), size = Size(len, thick))
    drawRect(color, topLeft = Offset(width - thick, height - len), size = Size(thick, len))
}

/**
 * The CRT scanline wash, laid over the whole app.
 *
 * Two things were wrong with the previous implementation. It used [drawBehind]
 * on the `Scaffold`, whose own container colour is opaque — so every one of
 * those ~300 `drawRect` calls per frame was painted and then completely covered.
 * The effect the README advertises was not visible at all, and was costing GPU
 * time to stay invisible.
 *
 * It also tinted the entire screen red in proportion to how far the user was
 * from their calorie target, up to a 20% red wash over every pixel. Colour that
 * strong is a persistent alarm, and it degraded contrast on the actual data
 * underneath it.
 *
 * Now: drawn over the content so it is real, one repeating-gradient rect instead
 * of a loop, and a fixed, very low alpha that never depends on how someone ate.
 *
 * [enabled] backs the "Reduce visual effects" Settings toggle. The alpha here
 * is already low enough that this isn't fixing a measured problem — it's a
 * floor for anyone who wants every last bit of contrast, at any font scale,
 * with zero overlay in the way.
 */
fun Modifier.terminalOverlay(enabled: Boolean = true): Modifier =
    if (!enabled) this else this.drawWithCache {
        val lineHeight = 2.dp.toPx()
        val spacing = 4.dp.toPx()
        // One rect with a repeating gradient, rather than a drawRect per line.
        val brush = Brush.verticalGradient(
            0f to SCANLINE_COLOR,
            (lineHeight / spacing) to SCANLINE_COLOR,
            (lineHeight / spacing) to Color.Transparent,
            1f to Color.Transparent,
            startY = 0f,
            endY = spacing,
            tileMode = TileMode.Repeated
        )
        onDrawWithContent {
            drawContent()
            drawRect(brush = brush)
        }
    }

/**
 * Low enough to read as phosphor texture rather than a grille. Anything heavier
 * competes with the text it sits on top of, which matters more here than usual:
 * the primary content is small numbers.
 */
private val SCANLINE_COLOR = Color.White.copy(alpha = 0.03f)
