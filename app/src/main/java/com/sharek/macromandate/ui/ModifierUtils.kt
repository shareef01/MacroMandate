package com.sharek.macromandate.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sharek.macromandate.viewmodel.ComplianceStatus

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

fun Modifier.terminalOverlay(status: ComplianceStatus): Modifier = this.drawBehind {
    val scanlineColor = when (status) {
        ComplianceStatus.CRISIS, ComplianceStatus.LOCKED -> Color.Red.copy(alpha = 0.3f)
        ComplianceStatus.SUBVERSIVE -> Color.Red.copy(alpha = 0.15f)
        else -> Color.White.copy(alpha = 0.05f)
    }
    
    val lineHeight = 2.dp.toPx()
    val spacing = 4.dp.toPx()
    var y = 0f
    while (y < size.height) {
        drawRect(
            color = scanlineColor,
            topLeft = Offset(0f, y),
            size = Size(size.width, lineHeight)
        )
        y += spacing
    }
    
    if (status == ComplianceStatus.SUBVERSIVE) {
        drawRect(
            color = Color.Red.copy(alpha = 0.05f),
            topLeft = Offset(0f, 0f),
            size = size
        )
    }

    if (status == ComplianceStatus.CRISIS || status == ComplianceStatus.LOCKED) {
        drawRect(
            color = Color.Red.copy(alpha = 0.2f),
            topLeft = Offset(0f, 0f),
            size = size
        )
    }
}
