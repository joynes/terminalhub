package se.joynes.terminalhub.ui.screen.terminal

import android.view.KeyEvent
import android.view.MotionEvent
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalViewClient

/**
 * Routes terminal keyboard input through the attached [TerminalSession] and modifier state
 * to the SpecialKeyBar.
 *
 * We still intercept input here so Compose-managed modifiers and custom key mappings apply,
 * but the bytes now go through [TerminalSession.write] so terminal-generated replies and user
 * input share the same transport path.
 */
class TerminalViewClientImpl(
    private val modifierManager: MutableModifierManager,
    val onSendToSsh: (ByteArray) -> Unit,
    private val onTerminalTap: () -> Unit,
    private val onSearch: (String) -> Unit = {}
) : TerminalViewClient {

    // ── Modifier state ────────────────────────────────────────────────────────
    override fun readControlKey() = modifierManager.ctrl
    override fun readAltKey()     = modifierManager.alt
    override fun readShiftKey()   = modifierManager.shift
    override fun readFnKey()      = false

    // ── Character input ───────────────────────────────────────────────────────
    // Called by TerminalView for each typed character (after IME / modifier processing).
    // Return true = we handled it (don't write to session subprocess).
    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?): Boolean {
        val bytes = when {
            ctrlDown -> byteArrayOf((codePoint and 0x1F).toByte())
            modifierManager.alt -> ("\u001B" + String(Character.toChars(codePoint)))
                .toByteArray(Charsets.UTF_8)
            else -> String(Character.toChars(codePoint)).toByteArray(Charsets.UTF_8)
        }
        modifierManager.clearTransients()
        session?.write(bytes, 0, bytes.size) ?: onSendToSsh(bytes)
        return true
    }

    // Called for raw key events. Handle special keys here; return false for
    // printable characters so TerminalView calls onCodePoint for them.
    override fun onKeyDown(keyCode: Int, e: KeyEvent?, currentSession: TerminalSession?): Boolean {
        val seq = specialKeySequence(keyCode) ?: return false
        val bytes = seq.toByteArray(Charsets.UTF_8)
        modifierManager.clearTransients()
        currentSession?.write(bytes, 0, bytes.size) ?: onSendToSsh(bytes)
        return true
    }

    override fun onKeyUp(keyCode: Int, e: KeyEvent?) = false
    override fun onLongPress(event: MotionEvent?) = false

    // ── Touch ─────────────────────────────────────────────────────────────────
    override fun onScale(scale: Float) = scale
    override fun onSingleTapUp(e: MotionEvent?) { onTerminalTap() }

    // ── Flags ─────────────────────────────────────────────────────────────────
    override fun shouldBackButtonBeMappedToEscape() = false
    // Match upstream Termux default so soft keyboards use commitText()/character-based
    // input instead of relying on TYPE_NULL key events, which is unreliable on Android IMEs.
    override fun shouldEnforceCharBasedInput()       = true
    override fun shouldUseCtrlSpaceWorkaround()      = false
    override fun isTerminalViewSelected()             = true
    override fun copyModeChanged(copyMode: Boolean)  {}
    override fun onEmulatorSet()                     {}
    override fun onSearchRequested(selectedText: String) = onSearch(selectedText)

    // ── Logging (no-op) ───────────────────────────────────────────────────────
    override fun logError(tag: String?, message: String?) {}
    override fun logWarn(tag: String?, message: String?) {}
    override fun logInfo(tag: String?, message: String?) {}
    override fun logDebug(tag: String?, message: String?) {}
    override fun logVerbose(tag: String?, message: String?) {}
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
    override fun logStackTrace(tag: String?, e: Exception?) {}

    // ── Key → escape sequence ─────────────────────────────────────────────────
    private fun specialKeySequence(keyCode: Int): String? = when (keyCode) {
        KeyEvent.KEYCODE_ENTER         -> "\r"
        KeyEvent.KEYCODE_DPAD_UP       -> "\u001B[A"
        KeyEvent.KEYCODE_DPAD_DOWN     -> "\u001B[B"
        KeyEvent.KEYCODE_DPAD_RIGHT    -> "\u001B[C"
        KeyEvent.KEYCODE_DPAD_LEFT     -> "\u001B[D"
        KeyEvent.KEYCODE_PAGE_UP       -> "\u001B[5~"
        KeyEvent.KEYCODE_PAGE_DOWN     -> "\u001B[6~"
        KeyEvent.KEYCODE_MOVE_HOME     -> "\u001B[H"
        KeyEvent.KEYCODE_MOVE_END      -> "\u001B[F"
        KeyEvent.KEYCODE_DEL           -> "\u007F"
        KeyEvent.KEYCODE_FORWARD_DEL   -> "\u001B[3~"
        KeyEvent.KEYCODE_ESCAPE        -> "\u001B"
        KeyEvent.KEYCODE_TAB           -> "\t"
        KeyEvent.KEYCODE_F1            -> "\u001BOP"
        KeyEvent.KEYCODE_F2            -> "\u001BOQ"
        KeyEvent.KEYCODE_F3            -> "\u001BOR"
        KeyEvent.KEYCODE_F4            -> "\u001BOS"
        KeyEvent.KEYCODE_F5            -> "\u001B[15~"
        KeyEvent.KEYCODE_F6            -> "\u001B[17~"
        KeyEvent.KEYCODE_F7            -> "\u001B[18~"
        KeyEvent.KEYCODE_F8            -> "\u001B[19~"
        KeyEvent.KEYCODE_F9            -> "\u001B[20~"
        KeyEvent.KEYCODE_F10           -> "\u001B[21~"
        KeyEvent.KEYCODE_F11           -> "\u001B[23~"
        KeyEvent.KEYCODE_F12           -> "\u001B[24~"
        else                           -> null
    }
}
