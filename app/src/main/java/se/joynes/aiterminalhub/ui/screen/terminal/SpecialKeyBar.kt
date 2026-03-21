package se.joynes.aiterminalhub.ui.screen.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import se.joynes.aiterminalhub.ui.components.RetroButton
import se.joynes.aiterminalhub.ui.theme.MegaDriveSurface

private val SPECIAL_KEYS = listOf(
    "ESC" to "\u001B",
    "TAB" to "\t",
    "CTRL-C" to "\u0003",
    "CTRL-D" to "\u0004",
    "CTRL-Z" to "\u001A",
    "UP" to "\u001B[A",
    "DOWN" to "\u001B[B",
    "LEFT" to "\u001B[D",
    "RIGHT" to "\u001B[C",
    "HOME" to "\u001B[H",
    "END" to "\u001B[F"
)

@Composable
fun SpecialKeyBar(
    onKey: (String) -> Unit,
    onPaste: () -> Unit = {},
    onKeyboardToggle: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MegaDriveSurface)
            .horizontalScroll(rememberScrollState())
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        RetroButton("KBD", onKeyboardToggle)
        SPECIAL_KEYS.forEach { (label, value) ->
            RetroButton(label, { onKey(value) })
        }
        RetroButton("PASTE", onPaste)
    }
}
