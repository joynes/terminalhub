package se.joynes.terminalhub.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import se.joynes.terminalhub.ui.theme.MegaDrivePrimary
import se.joynes.terminalhub.ui.theme.MonoFontFamily
import se.joynes.terminalhub.ui.theme.rememberBlinkState

@Composable
fun BlinkingCursor(modifier: Modifier = Modifier) {
    val visible by rememberBlinkState()
    Text(
        text = if (visible) "_" else " ",
        color = MegaDrivePrimary,
        fontSize = 14.sp,
        fontFamily = MonoFontFamily,
        modifier = modifier
    )
}
