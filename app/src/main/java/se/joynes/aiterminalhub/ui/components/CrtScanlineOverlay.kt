package se.joynes.aiterminalhub.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import se.joynes.aiterminalhub.ui.theme.rememberScanlineAlpha

@Composable
fun CrtScanlineOverlay(modifier: Modifier = Modifier) {
    val alpha by rememberScanlineAlpha()
    Canvas(modifier = modifier) {
        val lineSpacing = 4f
        var y = 0f
        while (y < size.height) {
            drawLine(
                color = Color.Black.copy(alpha = alpha),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 2f
            )
            y += lineSpacing
        }
    }
}
