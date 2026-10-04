package se.joynes.terminalhub.ui.screen.sessions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import se.joynes.terminalhub.data.notes.*

@Composable
fun ProjectNotesPanel(
    projectName: String,
    state: ProjectNoteState,
    onEdit: (String) -> Unit,
    onClear: () -> Unit,
    onRetry: () -> Unit,
    onResolve: (Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var viewBoth by rememberSaveable { mutableStateOf(false) }
    Surface(modifier.fillMaxWidth().heightIn(max = 440.dp), tonalElevation = 8.dp,
        shadowElevation = 8.dp) {
        Column(Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("NOTES · $projectName", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = onClose) { Text("CLOSE") }
            }
            Text(when (state.status) {
                NoteSyncStatus.LOCAL_ONLY -> "Local only · export a backup before reinstalling"
                NoteSyncStatus.SYNCING -> "Saving…"
                NoteSyncStatus.SYNCED -> "Synced"
                NoteSyncStatus.OFFLINE_PENDING -> "Offline · pending sync"
                NoteSyncStatus.CONFLICT -> "Sync conflict"
                NoteSyncStatus.ERROR -> "Could not sync"
            }, style = MaterialTheme.typography.bodySmall)
            if (state.status == NoteSyncStatus.SYNCING) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.note?.syncError?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (state.note?.pendingDelete == true) Text("Clear pending · server copy will be removed after sync", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = state.note?.text.orEmpty(),
                onValueChange = { if (it.toByteArray(Charsets.UTF_8).size <= MAX_NOTE_BYTES) onEdit(it) },
                enabled = state.loaded,
                placeholder = { Text("Write plans, reminders or ideas…") },
                minLines = 4, maxLines = 8,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Project note" }
            )
            Text("Up to 256 KB · never sent to the terminal", style = MaterialTheme.typography.labelSmall)
            Row {
                TextButton(onClick = { clipboard.setText(AnnotatedString(state.note?.text.orEmpty())) }) { Text("COPY") }
                TextButton(onClick = { confirmClear = true }, enabled = state.loaded) { Text("CLEAR") }
                TextButton(onClick = onRetry, enabled = state.loaded && state.status != NoteSyncStatus.SYNCING) { Text("RETRY SYNC") }
            }
            if (state.conflict != null) {
                Text("This device and the server both changed. Choose which version to keep.")
                TextButton(onClick = { onResolve(true) }) { Text("KEEP THIS DEVICE") }
                TextButton(onClick = { onResolve(false) }) { Text("USE SERVER VERSION") }
                TextButton(onClick = { viewBoth = !viewBoth }) { Text("VIEW BOTH") }
                if (viewBoth) {
                    Text("THIS DEVICE\n${state.note?.text.orEmpty()}")
                    HorizontalDivider()
                    Text("SERVER\n${state.conflict.text ?: "(No note)"}")
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString("THIS DEVICE\n${state.note?.text.orEmpty()}\n\nSERVER\n${state.conflict.text.orEmpty()}"))
                    }) { Text("COPY BOTH") }
                }
            }
        }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Clear project note?") },
        text = { Text("This also removes the server copy when connected. Offline, removal stays pending until sync.") },
        confirmButton = { TextButton(onClick = { confirmClear = false; onClear() }) { Text("CLEAR NOTE") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("CANCEL") } }
    )
}
