package se.joynes.aiterminalhub.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

@Composable
fun rememberBlinkState(periodMs: Int = 600): State<Boolean> {
    val infiniteTransition = rememberInfiniteTransition(label = "blink")
    val blinkFloat by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(periodMs, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blinkFloat"
    )
    return derivedStateOf { blinkFloat > 0.5f }
}

@Composable
fun rememberScanlineAlpha(): State<Float> {
    val infiniteTransition = rememberInfiniteTransition(label = "scanline")
    return infiniteTransition.animateFloat(
        initialValue = 0.03f,
        targetValue = 0.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanlineAlpha"
    )
}
