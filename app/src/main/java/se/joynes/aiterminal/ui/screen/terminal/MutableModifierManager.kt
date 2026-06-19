package se.joynes.aiterminal.ui.screen.terminal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Compose-state-backed modifier holder shared between [SpecialKeyBar] and [TerminalViewClientImpl].
 * When CTRL/ALT/SHIFT is toggled in the key bar, [TerminalViewClientImpl] reads these flags
 * for the next key press, then [clearTransients] auto-resets the modifier.
 */
class MutableModifierManager {
    var ctrl  by mutableStateOf(false)
    var alt   by mutableStateOf(false)
    var shift by mutableStateOf(false)

    fun toggleCtrl()  { ctrl = !ctrl; alt = false; shift = false }
    fun toggleAlt()   { alt = !alt; ctrl = false; shift = false }
    fun toggleShift() { shift = !shift; ctrl = false; alt = false }

    fun clearTransients() { ctrl = false; alt = false; shift = false }
}
