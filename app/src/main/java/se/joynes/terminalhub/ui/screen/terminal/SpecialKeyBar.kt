package se.joynes.terminalhub.ui.screen.terminal

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.joynes.terminalhub.data.settings.KeyBarLayoutConfig
import se.joynes.terminalhub.ui.theme.*

private val KEY_H = 34.dp

@Composable
fun SpecialKeyBar(
    modifierManager: MutableModifierManager,
    onKey: (String) -> Unit,
    rows: List<List<String>> = KeyBarLayoutConfig.defaultRows,
    onPaste: () -> Unit = {},
    onTextInput: () -> Unit = {},
    onFileUpload: () -> Unit = {},
    onFileDownload: () -> Unit = {},
    onKeyboardToggle: () -> Unit = {},
    onPrevTab: () -> Unit = {},
    onNextTab: () -> Unit = {}
) {
    val ctrlActive  = modifierManager.ctrl
    val altActive   = modifierManager.alt
    val shiftActive = modifierManager.shift
    val normalizedRows = remember(rows) { KeyBarLayoutConfig.normalize(rows) }

    fun modified(normal: String): String {
        // Read the manager directly. A second, very quick tap can arrive before Compose
        // has recomposed the active-state colors and their captured snapshot values.
        val result = applyKeyBarModifiers(
            normal = normal,
            ctrl = modifierManager.ctrl,
            alt = modifierManager.alt,
            shift = modifierManager.shift
        )
        modifierManager.clearTransients()
        return result
    }

    fun arrowKey(letter: Char): String {
        val modBits = (if (modifierManager.shift) 1 else 0) or
                      (if (modifierManager.alt)   2 else 0) or
                      (if (modifierManager.ctrl)  4 else 0)
        modifierManager.clearTransients()
        return if (modBits == 0) "\u001B[$letter"
        else "\u001B[1;${modBits + 1}$letter"
    }

    fun press(keyId: String) {
        val definition = KeyBarLayoutConfig.definition(keyId) ?: return
        when (keyId) {
            "ESC" -> { modifierManager.clearTransients(); onKey("\u001B") }
            "TAB" -> onKey(modified("\t"))
            "ENTER" -> { modifierManager.clearTransients(); onKey("\r") }
            "BACKSPACE" -> { modifierManager.clearTransients(); onKey("\u007F") }
            "CTRL" -> modifierManager.toggleCtrl()
            "ALT" -> modifierManager.toggleAlt()
            "SHIFT" -> modifierManager.toggleShift()
            "UP" -> onKey(arrowKey('A'))
            "DOWN" -> onKey(arrowKey('B'))
            "RIGHT" -> onKey(arrowKey('C'))
            "LEFT" -> onKey(arrowKey('D'))
            "HOME" -> { modifierManager.clearTransients(); onKey("\u001B[H") }
            "END" -> { modifierManager.clearTransients(); onKey("\u001B[F") }
            "PAGE_UP" -> { modifierManager.clearTransients(); onKey("\u001B[5~") }
            "PAGE_DOWN" -> { modifierManager.clearTransients(); onKey("\u001B[6~") }
            "KEYBOARD" -> onKeyboardToggle()
            "TEXT_INPUT" -> onTextInput()
            "UPLOAD" -> onFileUpload()
            "DOWNLOAD" -> onFileDownload()
            "PASTE" -> onPaste()
            else -> definition.text?.let { onKey(modified(it)) }
        }
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
        normalizedRows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                row.forEach { keyId ->
                    val active = when (keyId) {
                        "CTRL" -> ctrlActive
                        "ALT" -> altActive
                        "SHIFT" -> shiftActive
                        else -> false
                    }
                    TermKey(
                        label = compactLabel(keyId),
                        modifier = Modifier.weight(1f),
                        active = active,
                        fontSize = if (keyId in LARGE_GLYPH_KEYS) 16.sp else if (row.size > 10) 9.sp else 11.sp,
                        onClick = { press(keyId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TermKey(
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    fontSize: androidx.compose.ui.unit.TextUnit = 11.sp,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .then(modifier)
            .height(KEY_H)
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

private val LARGE_GLYPH_KEYS = setOf("TAB", "ENTER", "SHIFT", "UP", "DOWN", "LEFT", "RIGHT", "KEYBOARD", "TEXT_INPUT", "UPLOAD", "DOWNLOAD")

internal fun applyKeyBarModifiers(
    normal: String,
    ctrl: Boolean,
    alt: Boolean,
    shift: Boolean
): String {
    val shifted = if (shift && normal.length == 1 && normal[0].isLetter()) {
        normal.uppercase()
    } else {
        normal
    }
    if (ctrl) {
        val controlChar = normal.lowercase().singleOrNull()
        if (controlChar != null && controlChar in 'a'..'z') {
            // ASCII control characters are the letter code with the upper three bits
            // cleared. In particular, lowercase c (0x63) must become ETX (0x03),
            // not '#' (0x23), which XOR 0x40 would produce.
            return (controlChar.code and 0x1F).toChar().toString()
        }
    }
    return if (alt) "\u001B$shifted" else shifted
}

private fun compactLabel(keyId: String): String = when (keyId) {
    "TAB" -> "⇥"
    "ENTER" -> "↵"
    "BACKSPACE" -> "⌫"
    "SHIFT" -> "⇧"
    "UP" -> "↑"
    "DOWN" -> "↓"
    "LEFT" -> "←"
    "RIGHT" -> "→"
    "PAGE_UP" -> "PG↑"
    "PAGE_DOWN" -> "PG↓"
    "KEYBOARD" -> "⌨"
    "TEXT_INPUT" -> "✎"
    "UPLOAD" -> "+"
    "DOWNLOAD" -> "⇩"
    else -> KeyBarLayoutConfig.definition(keyId)?.label ?: keyId
}
