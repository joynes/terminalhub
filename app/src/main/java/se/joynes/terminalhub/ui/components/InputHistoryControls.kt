package se.joynes.terminalhub.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

@Composable
fun InputHistoryEnableDialog(onDismiss: () -> Unit, onEnable: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save terminal input history?") },
        text = { Text("TerminalHub can save text you send to terminals so you can reuse it from Recent and create pinned actions.\n\n" +
            "Terminal input may contain passwords, API keys, tokens, commands or other sensitive information.\n\n" +
            "History is stored locally on this device.") },
        confirmButton = { TextButton(onClick = onEnable) { Text("ENABLE HISTORY") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } }
    )
}

@Composable
fun InputHistorySettings(enabled: Boolean, onEnabledChange: (Boolean) -> Unit, onDeleteHistory: () -> Unit) {
    var confirmEnable by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    Column {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Save terminal input history", style = MaterialTheme.typography.titleSmall)
                Text(if (enabled) "On for all projects" else "Off", style = MaterialTheme.typography.bodySmall)
            }
            Switch(enabled, onCheckedChange = {
                if (it) confirmEnable = true else onEnabledChange(false)
            }, modifier = Modifier.semantics { contentDescription = "Save terminal input history" })
        }
        Text("Save sent text in Recent. Turning this off keeps previously saved history. Pinned actions work with history off.", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { confirmDelete = true }) { Text("Delete saved input history") }
    }
    if (confirmEnable) InputHistoryEnableDialog(
        onDismiss = { confirmEnable = false },
        onEnable = { confirmEnable = false; onEnabledChange(true) }
    )
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete all saved terminal input history?") },
        text = { Text("This cannot be undone. Pinned actions will be kept.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDeleteHistory() }) { Text("DELETE HISTORY") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("CANCEL") } }
    )
}
