package se.joynes.terminalhub.ui.screen.settings

import android.view.KeyEvent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import se.joynes.terminalhub.data.settings.*
import se.joynes.terminalhub.ui.components.*
import se.joynes.terminalhub.ui.theme.*

@Composable
internal fun KeyboardShortcutsEditor(rows: List<String>, onChange: (List<String>) -> Unit) {
    val bindings = remember(rows) { KeyboardShortcuts.parse(rows) }
    var recording by remember { mutableStateOf<ShortcutAction?>(null) }
    Text("Bluetooth and USB keyboards. Tap an action, then press its key combination. Shortcuts work on the terminal screen and take priority over terminal input. Android may reserve some combinations; avoid keys you use with AltGr.", color = MegaDriveDim)
    TextButton(onClick = { onChange(KeyboardShortcuts.suggestions) }) { Text("Use suggested shortcuts") }
    ShortcutAction.entries.forEach { action ->
        Row(Modifier.fillMaxWidth()) {
            TextButton(onClick = { recording = action }, modifier = Modifier.weight(1f)) {
                Column(Modifier.fillMaxWidth()) {
                    Text(action.label, color = MegaDriveOnSurface)
                    Text(bindings[action]?.encode()?.replace("+", " + ") ?: "Not assigned", color = MegaDrivePrimary)
                }
            }
            if (action in bindings) TextButton(onClick = { onChange(KeyboardShortcuts.assign(rows, action, null)) }) { Text("Clear") }
        }
    }
    recording?.let { action ->
        var chord by remember(action) { mutableStateOf<ShortcutChord?>(null) }
        var error by remember(action) { mutableStateOf<String?>(null) }
        val conflict = bindings.entries.firstOrNull { it.key != action && it.value == chord }?.key
        AlertDialog(onDismissRequest = { recording = null }, title = { Text("Shortcut: ${action.label}") }, text = {
            WindowKeyHandler { event ->
                if (!isShortcutKeyboardEvent(event)) false
                else if (event.keyCode == KeyEvent.KEYCODE_ESCAPE && event.metaState == 0) {
                    if (event.action == KeyEvent.ACTION_DOWN) recording = null
                    true
                } else {
                    if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && !KeyEvent.isModifierKey(event.keyCode)) {
                        val captured = shortcutChord(event)
                        val invalid = runCatching { KeyboardShortcuts.validate(captured) }.exceptionOrNull()?.message
                        error = invalid
                        chord = captured.takeIf { invalid == null }
                    }
                    true
                }
            }
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Press a combination on your external keyboard. Use Ctrl, Alt or Meta, or an F1–F12 key. Escape cancels.")
                Text(chord?.encode()?.replace("+", " + ") ?: "Waiting for keys…", color = MegaDrivePrimary)
                error?.let { Text(it, color = MegaDriveWarning) }
                conflict?.let { Text("Already assigned to ${it.label}. Clear that action first or choose another combination.", color = MegaDriveWarning) }
            }
        }, confirmButton = {
            TextButton(enabled = chord != null && conflict == null, onClick = {
                onChange(KeyboardShortcuts.assign(rows, action, chord)); recording = null
            }) { Text("SAVE") }
        }, dismissButton = { TextButton(onClick = { recording = null }) { Text("CANCEL") } })
    }
}
