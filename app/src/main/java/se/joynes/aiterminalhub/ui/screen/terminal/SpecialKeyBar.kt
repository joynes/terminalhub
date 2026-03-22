package se.joynes.aiterminalhub.ui.screen.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.joynes.aiterminalhub.ui.theme.*

private val KEY_H = 36.dp
private val KEY_W = 56.dp        // regular key
private val MOD_W = 64.dp        // wider modifier keys (CTRL/ALT/SHIFT)
private val ARROW_W = 48.dp

@Composable
fun SpecialKeyBar(
    modifierManager: MutableModifierManager,
    onKey: (String) -> Unit,
    onPaste: () -> Unit = {},
    onKeyboardToggle: () -> Unit = {}
) {
    val ctrlActive  = modifierManager.ctrl
    val altActive   = modifierManager.alt
    val shiftActive = modifierManager.shift

    // Apply modifier to a string key (ESC, TAB, :, etc.) and reset modifiers.
    // Arrow keys have their own ANSI sequences and are handled by [arrow].
    fun modified(normal: String): String {
        val result = when {
            ctrlActive -> {
                if (normal.length == 1 && normal[0] in 'a'..'z')
                    (normal[0].code xor 0x40).toChar().toString()
                else normal
            }
            altActive -> "\u001B$normal"
            else -> normal
        }
        modifierManager.clearTransients()
        return result
    }

    fun arrow(normal: String, ctrl: String, alt: String, shift: String): String {
        val result = when {
            ctrlActive  -> ctrl
            altActive   -> alt
            shiftActive -> shift
            else        -> normal
        }
        modifierManager.clearTransients()
        return result
    }

    // Two-row layout, keyboard-inspired:
    //  Row 1:  ESC | TAB | :     [spacer]   ↑        ⌨
    //  Row 2:  CTRL | ALT | SHIFT [spacer]  ← | ↓ | →
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MegaDriveSurface)
            .padding(horizontal = 4.dp, vertical = 3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        // ── Row 1 ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            TermKey("ESC", KEY_W, active = false) { onKey(modified("\u001B")) }
            TermKey("TAB", KEY_W, active = false) { onKey(modified("\t")) }
            TermKey(":",   KEY_W, active = false) { onKey(modified(":")) }

            Spacer(Modifier.weight(1f))

            // ↑ sits above the ↓ in row 2 — offset by (ARROW_W + 3dp) from right edge
            TermKey("↑", ARROW_W, active = false) {
                onKey(arrow("\u001B[A", "\u001B[1;5A", "\u001B[1;3A", "\u001B[1;2A"))
            }

            Spacer(Modifier.width(ARROW_W * 2 + 3.dp * 2))   // placeholder for ← ↓ →  keep ↑ aligned over ↓

            TermKey("⌨", KEY_W, active = false, onClick = onKeyboardToggle)
        }

        // ── Row 2 ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            TermKey("CTRL",  MOD_W, active = ctrlActive)  { modifierManager.toggleCtrl() }
            TermKey("ALT",   MOD_W, active = altActive)   { modifierManager.toggleAlt() }
            TermKey("SHIFT", MOD_W, active = shiftActive) { modifierManager.toggleShift() }

            Spacer(Modifier.weight(1f))

            TermKey("←", ARROW_W, active = false) {
                onKey(arrow("\u001B[D", "\u001B[1;5D", "\u001B[1;3D", "\u001B[1;2D"))
            }
            TermKey("↓", ARROW_W, active = false) {
                onKey(arrow("\u001B[B", "\u001B[1;5B", "\u001B[1;3B", "\u001B[1;2B"))
            }
            TermKey("→", ARROW_W, active = false) {
                onKey(arrow("\u001B[C", "\u001B[1;5C", "\u001B[1;3C", "\u001B[1;2C"))
            }

            Spacer(Modifier.width(KEY_W + 3.dp))   // align with ⌨ above
        }
    }
}

@Composable
private fun TermKey(
    label: String,
    width: Dp,
    active: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(KEY_H)
            .width(width)
            .clip(RoundedCornerShape(5.dp))
            .background(if (active) MegaDrivePrimary else MegaDriveBg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (active) MegaDriveBg else MegaDrivePrimary,
            fontSize = 12.sp,
            fontFamily = MonoFontFamily,
            textAlign = TextAlign.Center
        )
    }
}
