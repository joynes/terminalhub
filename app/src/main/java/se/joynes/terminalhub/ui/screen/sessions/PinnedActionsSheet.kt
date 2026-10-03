package se.joynes.terminalhub.ui.screen.sessions

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import se.joynes.terminalhub.data.db.entity.PinnedActionEntity
import se.joynes.terminalhub.data.db.entity.TextInputHistoryEntity
import se.joynes.terminalhub.data.model.pinnedActionName

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PinnedActionsSheet(
    projectId: Long,
    pins: List<PinnedActionEntity>,
    history: List<TextInputHistoryEntity>,
    initiallyRecent: Boolean = false,
    onDismiss: () -> Unit,
    onPrepare: (String) -> Unit,
    onSend: (PinnedActionEntity) -> Unit,
    onSave: (PinnedActionEntity) -> Unit,
    onDeletePin: (Long) -> Unit,
    onDeleteHistory: (Long) -> Unit
) {
    var recent by remember { mutableStateOf(initiallyRecent) }
    var visibleCount by remember { mutableIntStateOf(20) }
    var selectedHistory by remember { mutableStateOf<TextInputHistoryEntity?>(null) }
    var selectedPin by remember { mutableStateOf<PinnedActionEntity?>(null) }
    var editing by remember { mutableStateOf<PinnedActionEntity?>(null) }
    val clipboard = LocalClipboardManager.current

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text("Pinned actions", style = MaterialTheme.typography.titleLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(selected = !recent, onClick = { recent = false }, label = { Text("★ Pinned") })
                FilterChip(selected = recent, onClick = { recent = true }, label = { Text("Recent") })
                TextButton(onClick = { editing = PinnedActionEntity(name = "", text = "", scope = "GLOBAL") }) { Text("New") }
            }
            Text(
                if (recent) "Tap to edit. Hold to pin, copy or delete. History is local to this device and may contain sensitive input."
                else "Tap to send or prepare. Hold to edit or delete.",
                style = MaterialTheme.typography.bodySmall
            )
            LazyColumn(Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 480.dp).padding(vertical = 8.dp)) {
                if (recent) {
                    if (history.isEmpty()) item { Text("No recent input for this project yet.", Modifier.padding(16.dp)) }
                    items(history.take(visibleCount), key = { it.id }) { entry ->
                        ListItem(
                            headlineContent = { Text(textInputHistoryPreview(entry.text), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.combinedClickable(
                                onClick = { onPrepare(entry.text) },
                                onLongClick = { selectedHistory = entry }
                            ).heightIn(min = 56.dp)
                        )
                        HorizontalDivider()
                    }
                    if (history.size > visibleCount) item {
                        TextButton(onClick = { visibleCount += 20 }, modifier = Modifier.fillMaxWidth()) { Text("Show more") }
                    }
                } else {
                    if (pins.isEmpty()) item { Text("Pin recent input or tap New to create an action.", Modifier.padding(16.dp)) }
                    items(pins, key = { it.id }) { action ->
                        ListItem(
                            headlineContent = { Text(action.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text("${if (action.scope == "GLOBAL") "Global" else "Project"} · ${if (action.sendEnter) "Send + Enter" else "Prepare in Text Input"}") },
                            modifier = Modifier.combinedClickable(
                                onClick = { if (action.sendEnter) onSend(action) else onPrepare(action.text) },
                                onLongClick = { selectedPin = action }
                            ).heightIn(min = 64.dp)
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    selectedHistory?.let { entry ->
        AlertDialog(
            onDismissRequest = { selectedHistory = null },
            title = { Text("Recent input") },
            text = {
                Column {
                    Text(textInputHistoryPreview(entry.text), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = {
                        selectedHistory = null
                        editing = PinnedActionEntity(name = "", text = entry.text, scope = "GLOBAL")
                    }) { Text("Pin") }
                    TextButton(onClick = { clipboard.setText(AnnotatedString(entry.text)); selectedHistory = null }) { Text("Copy") }
                    TextButton(onClick = { selectedHistory = null; onPrepare(entry.text) }) { Text("Edit in Text Input") }
                    TextButton(onClick = { onDeleteHistory(entry.id); selectedHistory = null }) { Text("Delete from history") }
                }
            },
            confirmButton = { TextButton(onClick = { selectedHistory = null }) { Text("Close") } }
        )
    }
    selectedPin?.let { action ->
        AlertDialog(
            onDismissRequest = { selectedPin = null },
            title = { Text(action.name) },
            text = {
                Column {
                    TextButton(onClick = { selectedPin = null; editing = action }) { Text("Edit action") }
                    TextButton(onClick = { selectedPin = null; onPrepare(action.text) }) { Text("Prepare in Text Input") }
                    TextButton(onClick = { onDeletePin(action.id); selectedPin = null }) { Text("Delete action") }
                }
            },
            confirmButton = { TextButton(onClick = { selectedPin = null }) { Text("Close") } }
        )
    }
    editing?.let { initial ->
        key(initial) {
            var name by remember { mutableStateOf(initial.name) }
            var text by remember { mutableStateOf(initial.text) }
            var global by remember { mutableStateOf(initial.scope == "GLOBAL") }
            var sendEnter by remember { mutableStateOf(initial.sendEnter) }
            AlertDialog(
                onDismissRequest = { editing = null },
                title = { Text(if (initial.id == 0L) "Pin action" else "Edit action") },
                text = {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { OutlinedTextField(name, { name = it }, label = { Text("Name (optional)") },
                            placeholder = { Text(pinnedActionName("", text), maxLines = 1, overflow = TextOverflow.Ellipsis) }, singleLine = true) }
                        item { OutlinedTextField(text, { text = it }, label = { Text("Input") }, minLines = 2, maxLines = 5) }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(!global, { global = false }, label = { Text("Project") })
                                FilterChip(global, { global = true }, label = { Text("Global") })
                            }
                        }
                        item {
                            Row {
                                Checkbox(sendEnter, { sendEnter = it })
                                Text("Send immediately with Enter", Modifier.padding(top = 12.dp))
                            }
                            Text("Off: open in Text Input for editing.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    TextButton(enabled = text.isNotBlank(), onClick = {
                        onSave(initial.copy(name = pinnedActionName(name, text), text = text, scope = if (global) "GLOBAL" else "PROJECT", projectId = if (global) null else projectId, sendEnter = sendEnter))
                        editing = null
                        recent = false
                    }) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } }
            )
        }
    }
}
