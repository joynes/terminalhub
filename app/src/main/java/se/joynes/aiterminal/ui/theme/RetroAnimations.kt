package se.joynes.aiterminal.ui.theme

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
fun rememberScanlineAlpha(): State<Float> = remember { mutableStateOf(0.05f) }
