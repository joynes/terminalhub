package se.joynes.aiterminalhub.ui.screen.terminal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.connectbot.terminal.ModifierManager

/**
 * Compose-state-backed [ModifierManager] shared between [SpecialKeyBar] and the Terminal
 * composable. When CTRL/ALT/SHIFT is toggled in the key bar, the Terminal's KeyboardHandler
 * reads these flags for the next soft-keyboard key press, then calls [clearTransients] to
 * auto-reset the modifier.
 */
class MutableModifierManager : ModifierManager {
    var ctrl  by mutableStateOf(false)
    var alt   by mutableStateOf(false)
    var shift by mutableStateOf(false)

    fun toggleCtrl()  { ctrl = !ctrl; alt = false; shift = false }
    fun toggleAlt()   { alt = !alt; ctrl = false; shift = false }
    fun toggleShift() { shift = !shift; ctrl = false; alt = false }

    override fun isCtrlActive()  = ctrl
    override fun isAltActive()   = alt
    override fun isShiftActive() = shift
    override fun clearTransients() { ctrl = false; alt = false; shift = false }
}
