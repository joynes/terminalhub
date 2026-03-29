package se.joynes.aiterminalhub.ui.screen.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.joynes.aiterminalhub.ui.theme.*

private val KEY_H   = 34.dp
private val ESC_W   = 42.dp  // ESC slightly wider
private val KEY_W   = 28.dp  // ⇥ : / @ 1 2 3 ↑
private val RET_W   = 34.dp  // ↵
private val MOD_W   = 40.dp  // CTRL ALT ⇧
private val ACT_W   = 50.dp  // ⌨ pen + (action keys, row 2 center)
private val ARROW_W = 30.dp  // ← ↓ →

@Composable
fun SpecialKeyBar(
    modifierManager: MutableModifierManager,
    onKey: (String) -> Unit,
    onPaste: () -> Unit = {},
    onTextInput: () -> Unit = {},
    onFileUpload: () -> Unit = {},
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

    fun arrowKey(letter: Char): String {
        val modBits = (if (shiftActive) 1 else 0) or
                      (if (altActive)   2 else 0) or
                      (if (ctrlActive)  4 else 0)
        modifierManager.clearTransients()
        return if (modBits == 0) "\u001B[$letter"
        else "\u001B[1;${modBits + 1}$letter"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MegaDriveSurface)
            .padding(horizontal = 4.dp)
            .pointerInput(onPrevTab, onNextTab) {
                var totalDragX = 0f
                detectHorizontalDragGestures(
                    onDragStart      = { totalDragX = 0f },
                    onHorizontalDrag = { change, amount -> change.consume(); totalDragX += amount },
                    onDragEnd        = {
                        if (totalDragX < -80.dp.toPx()) onNextTab()
                        else if (totalDragX > 80.dp.toPx()) onPrevTab()
                    },
                    onDragCancel     = { totalDragX = 0f }
                )
            },
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Row 1: ESC  ⇥  :  /  @  1  2  3  [spacer]  ↑  ↵
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            TermKey("ESC", ESC_W) { modifierManager.clearTransients(); onKey("\u001B") }
            TermKey("⇥",   KEY_W) { onKey(modified("\t")) }
            TermKey(":",   KEY_W) { onKey(modified(":")) }
            TermKey("/",   KEY_W) { onKey(modified("/")) }
            TermKey("@",   KEY_W) { onKey(modified("@")) }
            TermKey("1",   KEY_W) { onKey(modified("1")) }
            TermKey("2",   KEY_W) { onKey(modified("2")) }
            TermKey("3",   KEY_W) { onKey(modified("3")) }
            Spacer(Modifier.weight(1f))
            TermKey("↑",   KEY_W) { onKey(arrowKey('A')) }
            TermKey("↵",   RET_W) { modifierManager.clearTransients(); onKey("\r") }
        }

        // Row 2: CTRL  ALT  SHIFT  [spacer]  ⌨  ✎  +  [spacer]  ←  ↓  →
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            TermKey("CTRL",  MOD_W, active = ctrlActive)  { modifierManager.toggleCtrl() }
            TermKey("ALT",   MOD_W, active = altActive)   { modifierManager.toggleAlt() }
            TermKey("⇧",     MOD_W, active = shiftActive, fontSize = 16.sp) { modifierManager.toggleShift() }
            Spacer(Modifier.weight(1f))
            TermKey("⌨", ACT_W, fontSize = 18.sp) { onKeyboardToggle() }
            IconTermKey(Icons.Default.Edit, "text input",  ACT_W, onClick = onTextInput)
            IconTermKey(Icons.Default.Add,  "file upload", ACT_W, onClick = onFileUpload)
            Spacer(Modifier.weight(1f))
            TermKey("←", ARROW_W) { onKey(arrowKey('D')) }
            TermKey("↓", ARROW_W) { onKey(arrowKey('B')) }
            TermKey("→", ARROW_W) { onKey(arrowKey('C')) }
        }
    }
}

@Composable
private fun TermKey(
    label: String,
    width: Dp,
    active: Boolean = false,
    fontSize: androidx.compose.ui.unit.TextUnit = 11.sp,
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
            fontSize = fontSize,
            fontFamily = MonoFontFamily,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun IconTermKey(
    icon: ImageVector,
    contentDescription: String,
    width: Dp,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(KEY_H)
            .width(width)
            .clip(RoundedCornerShape(4.dp))
            .background(MegaDriveBg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MegaDrivePrimary,
            modifier = Modifier.size(18.dp)
        )
    }
}
