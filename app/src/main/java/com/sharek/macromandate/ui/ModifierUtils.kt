package com.sharek.macromandate.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sharek.macromandate.viewmodel.ComplianceStatus

/**
 * Subtle tactical corner brackets for key terminal cards and viewfinders.
 */
fun Modifier.hudFraming(
    color: Color,
    length: Dp = 14.dp,
    thickness: Dp = 1.5.dp
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
 * Legacy terminal overlay modifier retained for call-site compatibility.
 * Scanline rendering is consolidated in [com.sharek.macromandate.ui.theme.crtFilter]
 * so disabling CRT effects disables all overlays cleanly and never washes the UI in red.
 */
@Suppress("UnusedReceiverParameter", "UNUSED_PARAMETER")
fun Modifier.terminalOverlay(status: ComplianceStatus): Modifier = this
