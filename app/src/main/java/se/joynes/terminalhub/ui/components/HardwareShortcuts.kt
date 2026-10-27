package se.joynes.terminalhub.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.InputDevice
import android.view.KeyEvent
import android.view.Window
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import se.joynes.terminalhub.data.settings.*

internal fun isShortcutKeyboardEvent(event: KeyEvent): Boolean =
    event.source and InputDevice.SOURCE_KEYBOARD == InputDevice.SOURCE_KEYBOARD &&
        event.flags and KeyEvent.FLAG_SOFT_KEYBOARD == 0

internal fun shortcutChord(event: KeyEvent) = ShortcutChord(event.keyCode,
    (if (event.isCtrlPressed) 1 else 0) or (if (event.isAltPressed) 2 else 0) or
        (if (event.isShiftPressed) 4 else 0) or (if (event.isMetaPressed) 8 else 0))

/** Consume the whole key press, including repeats and release after modifiers are released. */
internal class ShortcutKeyDispatcher {
    private val consumed = mutableSetOf<Pair<Int, Int>>()
    fun clear() = consumed.clear()
    fun dispatch(event: KeyEvent, bindings: Map<ShortcutAction, ShortcutChord>, onAction: (ShortcutAction) -> Unit): Boolean {
        if (!isShortcutKeyboardEvent(event)) return false
        val press = event.deviceId to event.keyCode
        if (event.action == KeyEvent.ACTION_UP) return consumed.remove(press)
        if (event.action != KeyEvent.ACTION_DOWN) return false
        if (event.repeatCount > 0 && press in consumed) return true
        if (event.repeatCount == 0) consumed.remove(press)
        val action = bindings.entries.firstOrNull { it.value == shortcutChord(event) }?.key ?: return false
        consumed.add(press)
        if (event.repeatCount == 0) onAction(action)
        return true
    }
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/** Intercept before either AndroidView/TerminalView or Compose text input receives the key. */
@Composable
internal fun WindowKeyHandler(onFocusLost: () -> Unit = {}, onKey: (KeyEvent) -> Boolean) {
    val view = LocalView.current
    val window = (view.parent as? DialogWindowProvider)?.window ?: view.context.activity()?.window
    val currentKey by rememberUpdatedState(onKey)
    val currentLost by rememberUpdatedState(onFocusLost)
    DisposableEffect(window) {
        val previous = window?.callback
        val wrapper = previous?.let {
            object : Window.Callback by it {
                override fun dispatchKeyEvent(event: KeyEvent): Boolean = currentKey(event) || it.dispatchKeyEvent(event)
                override fun dispatchKeyShortcutEvent(event: KeyEvent): Boolean = currentKey(event) || it.dispatchKeyShortcutEvent(event)
                override fun onWindowFocusChanged(hasFocus: Boolean) {
                    if (!hasFocus) currentLost()
                    it.onWindowFocusChanged(hasFocus)
                }
            }
        }
        if (wrapper != null) window.callback = wrapper
        onDispose { if (window?.callback === wrapper) window?.callback = previous; currentLost() }
    }
}

@Composable
internal fun HardwareShortcutHandler(rows: List<String>, onAction: (ShortcutAction) -> Unit) {
    val dispatcher = remember { ShortcutKeyDispatcher() }
    val bindings = remember(rows) { KeyboardShortcuts.parse(rows) }
    WindowKeyHandler(onFocusLost = dispatcher::clear) { dispatcher.dispatch(it, bindings, onAction) }
}
