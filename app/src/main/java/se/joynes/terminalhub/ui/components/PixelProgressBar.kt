package se.joynes.terminalhub.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.joynes.terminalhub.ui.theme.*

@Composable
fun PixelProgressBar(
    progress: Float,
    label: String = "",
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        if (label.isNotBlank()) {
            Text(label, color = MegaDrivePrimary, fontSize = 10.sp, fontFamily = MonoFontFamily)
            Spacer(Modifier.height(2.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(modifier = Modifier.weight(1f).height(12.dp)) {
                drawRect(color = MegaDriveSurface, size = size)
                drawRect(
                    color = MegaDrivePrimary,
                    size = Size(size.width * progress.coerceIn(0f, 1f), size.height)
                )
                // pixel grid lines
                val step = 8.dp.toPx()
                var x = step
                while (x < size.width) {
                    drawLine(MegaDriveBg, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                    x += step
                }
            }
            Spacer(Modifier.width(8.dp))
            Text("${(progress * 100).toInt()}%", color = MegaDrivePrimary, fontSize = 10.sp, fontFamily = MonoFontFamily)
        }
    }
}
