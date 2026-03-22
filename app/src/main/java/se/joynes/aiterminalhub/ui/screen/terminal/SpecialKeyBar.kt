package se.joynes.aiterminalhub.ui.screen.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
private val KEY_W = 52.dp        // regular key
private val MOD_W = 60.dp        // wider modifier keys (CTRL/ALT/SHIFT)
private val ARROW_W = 44.dp

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

    val scrollState1 = rememberScrollState()
    val scrollState2 = rememberScrollState()

    // Two-row layout. Left portion of each row is horizontally scrollable (swipe for more keys).
    // Right portion (arrows + keyboard toggle) is always visible.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MegaDriveSurface)
            .padding(horizontal = 4.dp, vertical = 3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        // ── Row 1: special chars (scrollable) + ↑ + ⌨ (fixed) ────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(scrollState1),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                TermKey("ESC",  KEY_W) { onKey(modified("\u001B")) }
                TermKey("TAB",  KEY_W) { onKey(modified("\t")) }
                TermKey(":",    KEY_W) { onKey(modified(":")) }
                TermKey("~",    KEY_W) { onKey(modified("~")) }
                TermKey("|",    KEY_W) { onKey(modified("|")) }
                TermKey("\\",   KEY_W) { onKey(modified("\\")) }
                TermKey("`",    KEY_W) { onKey(modified("`")) }
                TermKey("!",    KEY_W) { onKey(modified("!")) }
                TermKey("#",    KEY_W) { onKey(modified("#")) }
                TermKey("$",    KEY_W) { onKey(modified("$")) }
                TermKey("\"",   KEY_W) { onKey(modified("\"")) }
                TermKey("'",    KEY_W) { onKey(modified("'")) }
            }

            TermKey("↑", ARROW_W) {
                onKey(arrow("\u001B[A", "\u001B[1;5A", "\u001B[1;3A", "\u001B[1;2A"))
            }
            Spacer(Modifier.width(ARROW_W * 2 + 3.dp * 2)) // align ↑ over ↓
            TermKey("⌨", KEY_W, onClick = onKeyboardToggle)
        }

        // ── Row 2: modifiers (scrollable) + ← ↓ → (fixed) ────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(scrollState2),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                TermKey("CTRL",  MOD_W, active = ctrlActive)  { modifierManager.toggleCtrl() }
                TermKey("ALT",   MOD_W, active = altActive)   { modifierManager.toggleAlt() }
                TermKey("SHIFT", MOD_W, active = shiftActive) { modifierManager.toggleShift() }
                TermKey("PgUp",  MOD_W) {
                    onKey(arrow("\u001B[5~", "\u001B[5;5~", "\u001B[5;3~", "\u001B[5;2~"))
                }
                TermKey("PgDn",  MOD_W) {
                    onKey(arrow("\u001B[6~", "\u001B[6;5~", "\u001B[6;3~", "\u001B[6;2~"))
                }
                TermKey("Home",  MOD_W) {
                    onKey(arrow("\u001B[H", "\u001B[1;5H", "\u001B[1;3H", "\u001B[1;2H"))
                }
                TermKey("End",   MOD_W) {
                    onKey(arrow("\u001B[F", "\u001B[1;5F", "\u001B[1;3F", "\u001B[1;2F"))
                }
                TermKey("Del",   MOD_W) { onKey(modified("\u001B[3~")) }
            }

            TermKey("←", ARROW_W) {
                onKey(arrow("\u001B[D", "\u001B[1;5D", "\u001B[1;3D", "\u001B[1;2D"))
            }
            TermKey("↓", ARROW_W) {
                onKey(arrow("\u001B[B", "\u001B[1;5B", "\u001B[1;3B", "\u001B[1;2B"))
            }
            TermKey("→", ARROW_W) {
                onKey(arrow("\u001B[C", "\u001B[1;5C", "\u001B[1;3C", "\u001B[1;2C"))
            }
            Spacer(Modifier.width(KEY_W + 3.dp)) // align with ⌨ above
        }
    }
}

@Composable
private fun TermKey(
    label: String,
    width: Dp,
    active: Boolean = false,
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
            fontSize = 11.sp,
            fontFamily = MonoFontFamily,
            textAlign = TextAlign.Center
        )
    }
}
