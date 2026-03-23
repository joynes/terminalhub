package se.joynes.aiterminalhub.ui.screen.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.connectbot.terminal.VTermKey
import se.joynes.aiterminalhub.ui.theme.*

private val KEY_H   = 34.dp
private val KEY_W   = 48.dp   // regular key (ESC / TAB / : / / / @ / ↑ / ⌨)
private val MOD_W   = 58.dp   // CTRL / ALT / SHIFT
private val ARROW_W = 44.dp   // ← ↓ →

@Composable
fun SpecialKeyBar(
    modifierManager: MutableModifierManager,
    onKey: (String) -> Unit,
    onDispatchKey: (modifiers: Int, key: Int) -> Unit = { _, _ -> },
    onPaste: () -> Unit = {},
    onTextInput: () -> Unit = {},
    onKeyboardToggle: () -> Unit = {},
    onPrevTab: () -> Unit = {},
    onNextTab: () -> Unit = {}
) {
    val ctrlActive  = modifierManager.ctrl
    val altActive   = modifierManager.alt
    val shiftActive = modifierManager.shift

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

    fun modMask(): Int {
        val mask = (if (shiftActive) 1 else 0) or
                   (if (altActive)   2 else 0) or
                   (if (ctrlActive)  4 else 0)
        modifierManager.clearTransients()
        return mask
    }

    // Row 1:  ESC  TAB  :  /  @  [spacer]  RET  ↑  ⌨
    // Row 2:  CTRL  ALT  SHIFT  [spacer]  TXT  PST  ←  ↓  →
    // Swipe left/right on the bar to switch tabs.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MegaDriveSurface)
            .padding(horizontal = 4.dp, vertical = 3.dp)
            .pointerInput(onPrevTab, onNextTab) {
                var totalDragX = 0f
                detectHorizontalDragGestures(
                    onDragStart  = { totalDragX = 0f },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        totalDragX += amount
                    },
                    onDragEnd    = {
                        if (totalDragX < -80.dp.toPx()) onNextTab()
                        else if (totalDragX > 80.dp.toPx()) onPrevTab()
                    },
                    onDragCancel = { totalDragX = 0f }
                )
            },
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            TermKey("ESC", KEY_W, active = false) { onDispatchKey(0, VTermKey.ESCAPE) }
            TermKey("TAB", KEY_W, active = false) { onDispatchKey(0, VTermKey.TAB) }
            TermKey(":",   KEY_W, active = false) { onKey(modified(":")) }
            TermKey("/",   KEY_W, active = false) { onKey(modified("/")) }
            TermKey("@",   KEY_W, active = false) { onKey(modified("@")) }
            Spacer(Modifier.weight(1f))
            TermKey("PgUp", KEY_W, active = false) { onDispatchKey(0, VTermKey.PAGEUP) }
            TermKey("PgDn", KEY_W, active = false) { onDispatchKey(0, VTermKey.PAGEDOWN) }
            TermKey("RET", KEY_W, active = false) { onDispatchKey(0, VTermKey.ENTER) }
            TermKey("↑",   KEY_W, active = false) { onDispatchKey(modMask(), VTermKey.UP) }
            TermKey("⌨",   KEY_W, active = false, onClick = onKeyboardToggle)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            TermKey("CTRL",  MOD_W, active = ctrlActive)  { modifierManager.toggleCtrl() }
            TermKey("ALT",   MOD_W, active = altActive)   { modifierManager.toggleAlt() }
            TermKey("SHIFT", MOD_W, active = shiftActive) { modifierManager.toggleShift() }
            Spacer(Modifier.weight(1f))
            TermKey("TXT", KEY_W, active = false) { onTextInput() }
            TermKey("PST", KEY_W, active = false) { onPaste() }
            TermKey("←", ARROW_W, active = false) { onDispatchKey(modMask(), VTermKey.LEFT) }
            TermKey("↓", ARROW_W, active = false) { onDispatchKey(modMask(), VTermKey.DOWN) }
            TermKey("→", ARROW_W, active = false) { onDispatchKey(modMask(), VTermKey.RIGHT) }
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
            .clip(RoundedCornerShape(4.dp))
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
