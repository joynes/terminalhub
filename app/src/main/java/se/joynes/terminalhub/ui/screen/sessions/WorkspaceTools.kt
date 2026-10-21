package se.joynes.terminalhub.ui.screen.sessions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import se.joynes.terminalhub.ui.screen.download.DownloadState
import se.joynes.terminalhub.ui.screen.upload.UploadState
import se.joynes.terminalhub.ui.theme.MegaDrivePrimary

internal data class WorkspaceCommand(
    val id: String, val title: String, val keywords: String = "", val enabled: Boolean = true,
    val run: () -> Unit
)

internal fun filterWorkspaceCommands(commands: List<WorkspaceCommand>, query: String): List<WorkspaceCommand> {
    val words = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return commands.filter { command -> words.all { word ->
        "${command.title} ${command.keywords}".contains(word, ignoreCase = true)
    } }
}

@Composable
internal fun CommandPalette(commands: List<WorkspaceCommand>, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(commands, query) { filterWorkspaceCommands(commands, query) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Actions & projects") },
        text = {
            Column {
                OutlinedTextField(query, { query = it }, label = { Text("Find action or project") },
                    singleLine = true, modifier = Modifier.fillMaxWidth().testTag("command-query"))
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    if (filtered.isEmpty()) item { Text("No matching actions", Modifier.padding(12.dp)) }
                    items(filtered, key = { it.id }) { command ->
                        TextButton(onClick = { onDismiss(); command.run() }, enabled = command.enabled,
                            modifier = Modifier.fillMaxWidth()) { Text(command.title) }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}

internal fun connectionActivityLabel(tab: ProjectTabState): String = when {
    tab.isConnecting -> "Connecting…"
    tab.isConnected -> "Connected"
    !tab.connectionError.isNullOrBlank() -> "Connection failed"
    else -> "Disconnected"
}

internal fun transferActivityLabel(state: DownloadState): String? = when (state) {
    is DownloadState.Downloading -> "${state.fileName} · ${(state.progress * 100).toInt()}% · ${state.estimate.label()}"
    is DownloadState.Paused -> "${state.fileName} · Paused: ${state.message}"
    is DownloadState.BatchDone -> "${state.files.size} file(s) downloaded"
    is DownloadState.Error -> state.message
    else -> null
}

@Composable
internal fun WorkspaceActivityDialog(
    tabs: List<ProjectTabState>, transfers: Map<Long, DownloadState>,
    onSelect: (Long) -> Unit, onReconnect: (Long) -> Unit, onReconnectAll: () -> Unit,
    onTransfer: (Long) -> Unit, onDismiss: () -> Unit,
    uploads: Map<Long, UploadState> = emptyMap(), onUpload: (Long) -> Unit = {}
) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Connections & activity") },
        text = {
            LazyColumn(Modifier.heightIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (tabs.isEmpty()) item { Text("No open projects") }
                items(tabs, key = { it.projectId }) { tab ->
                    Column {
                        Text(tab.projectName, color = MegaDrivePrimary)
                        Row {
                            if (tab.isConnecting) CircularProgressIndicator(Modifier.size(16.dp).padding(end = 4.dp), strokeWidth = 2.dp)
                            Text(connectionActivityLabel(tab))
                        }
                        tab.connectionError?.takeIf { !tab.isConnected && !tab.isConnecting }?.let { Text(it) }
                        Row {
                            TextButton(onClick = { onDismiss(); onSelect(tab.projectId) }) { Text("Open project") }
                            if (!tab.isConnecting && tab.targetType == se.joynes.terminalhub.data.model.ProjectTargetType.SSH)
                                TextButton(onClick = { onReconnect(tab.projectId) }) { Text("Reconnect") }
                        }
                        HorizontalDivider()
                    }
                }
                item { Text("File transfers", color = MegaDrivePrimary) }
                val visible = transfers.mapNotNull { (id, state) -> transferActivityLabel(state)?.let { Triple(id, state, it) } }
                if (visible.isEmpty() && uploads.values.none { it !is UploadState.Idle }) item { Text("No file transfers") }
                items(visible, key = { "transfer:${it.first}" }) { (id, _, label) ->
                    Column {
                        Text(tabs.firstOrNull { it.projectId == id }?.projectName ?: "Project $id")
                        Text(label)
                        TextButton(onClick = { onDismiss(); onTransfer(id) }) { Text("Show transfer") }
                    }
                }
                items(uploads.entries.filter { it.value !is UploadState.Idle }, key = { "upload:${it.key}" }) { (id, state) ->
                    Column {
                        Text(tabs.firstOrNull { it.projectId == id }?.projectName ?: "Project $id")
                        Text(when (state) {
                            is UploadState.Uploading -> "Uploading ${state.fileName} · ${(state.progress * 100).toInt()}%"
                            is UploadState.Done -> "${state.fileName} · Uploaded"
                            is UploadState.Error -> state.message
                            else -> ""
                        })
                        TextButton(onClick = { onDismiss(); onUpload(id) }) { Text("Show upload") }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = onReconnectAll) { Text("Reconnect all") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}
