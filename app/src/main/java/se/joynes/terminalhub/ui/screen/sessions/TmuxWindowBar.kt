package se.joynes.terminalhub.ui.screen.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.joynes.terminalhub.data.ssh.TmuxWindow
import se.joynes.terminalhub.data.ssh.TmuxWindowCommands
import se.joynes.terminalhub.ui.theme.*

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TmuxWindowBar(
    state: TmuxWindowsState,
    inputBusy: Boolean,
    onCreate: (String) -> Unit,
    onSelect: (TmuxWindow) -> Unit,
    onRename: (TmuxWindow, String) -> Unit,
    onClose: (TmuxWindow) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    var creating by remember(state.projectId) { mutableStateOf(false) }
    var selectedMenu by remember(state.projectId) { mutableStateOf<TmuxWindow?>(null) }
    var renaming by remember(state.projectId) { mutableStateOf<TmuxWindow?>(null) }
    var closing by remember(state.projectId) { mutableStateOf<TmuxWindow?>(null) }
    val windows = state.snapshot?.windows.orEmpty()
    val enabled = state.connected && state.snapshot != null && !state.loading && !state.busy && !inputBusy
    val listState = rememberLazyListState()
    val activeIndex = windows.indexOfFirst { it.active }
    LaunchedEffect(state.projectId, activeIndex) { if (activeIndex >= 0) listState.animateScrollToItem(activeIndex) }

    Column(modifier.fillMaxWidth().background(MegaDriveBg).testTag("project-terminal-bar")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("TERMINALS", color = MegaDriveDim, fontFamily = MonoFontFamily, fontSize = 9.sp,
                modifier = Modifier.padding(horizontal = 6.dp))
            LazyRow(Modifier.weight(1f).testTag("project-terminal-tabs"), state = listState,
                horizontalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                itemsIndexed(windows, key = { _, window -> window.id }) { position, window ->
                    val name = window.name.map { if (it.isISOControl()) ' ' else it }.joinToString("").ifBlank { "Terminal ${window.index + 1}" }
                    Column(Modifier.widthIn(min = 70.dp, max = 180.dp)
                        .background(if (window.active) MegaDrivePrimary.copy(alpha = 0.18f) else MegaDriveSurface)
                        .combinedClickable(enabled = enabled, onClick = { if (!window.active) onSelect(window) },
                            onLongClick = { selectedMenu = window })
                        .semantics { selected = window.active; contentDescription = "Terminal ${position + 1}: $name" }
                        .padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Text("${position + 1} · $name", color = if (enabled || window.active) MegaDriveOnSurface else MegaDriveDim,
                            fontFamily = MonoFontFamily, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (window.panes > 1) Text("${window.panes} panes", color = MegaDriveDim, fontSize = 10.sp)
                    }
                }
            }
            if (state.loading || state.busy) {
                CircularProgressIndicator(Modifier.padding(horizontal = 8.dp).size(18.dp).testTag("project-terminals-progress"), strokeWidth = 2.dp)
            }
            TextButton(onClick = { creating = true }, enabled = enabled,
                modifier = Modifier.width(40.dp).semantics { contentDescription = "New terminal in project" }) { Text("+") }
            TextButton(onClick = onRefresh, enabled = state.connected && !state.busy && !state.loading,
                modifier = Modifier.width(40.dp).semantics { contentDescription = "Refresh project terminals" }) { Text("↻") }
        }
        val message = when {
            !state.connected -> "Reconnect to manage project terminals"
            state.busy -> state.operation
            inputBusy -> "Sending input — terminal switching is paused"
            state.error != null -> state.error
            else -> null
        }
        message?.let {
            Text(it, color = if (state.error != null) MegaDriveWarning else MegaDriveDim, fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
        }
    }

    if (creating) TerminalNameDialog("New terminal", "", optional = true,
        enabled = enabled, onDismiss = { creating = false }, onSave = { onCreate(it); creating = false })
    selectedMenu?.let { window ->
        AlertDialog(onDismissRequest = { selectedMenu = null }, title = { Text(window.name.ifBlank { "Terminal" }) },
            text = {
                Column {
                    TextButton(enabled = enabled, onClick = { selectedMenu = null; renaming = window }) { Text("Rename terminal") }
                    TextButton(enabled = enabled && windows.size > 1, onClick = { selectedMenu = null; closing = window }) { Text("Close terminal") }
                    if (windows.size == 1) Text("Keep one terminal in the project. Restart session starts the project again.", style = MaterialTheme.typography.bodySmall)
                }
            }, confirmButton = { TextButton(onClick = { selectedMenu = null }) { Text("DONE") } })
    }
    renaming?.let { window -> TerminalNameDialog("Rename terminal", window.name, optional = false,
        enabled = enabled, onDismiss = { renaming = null }, onSave = { onRename(window, it); renaming = null }) }
    closing?.let { window ->
        AlertDialog(onDismissRequest = { closing = null }, title = { Text("Close terminal?") },
            text = { Text("This stops programs running in ${window.name.ifBlank { "this terminal" }} and all its panes. Other terminals in the project keep running.") },
            confirmButton = { TextButton(enabled = enabled && windows.size > 1, onClick = { onClose(window); closing = null }) { Text("CLOSE TERMINAL") } },
            dismissButton = { TextButton(onClick = { closing = null }) { Text("CANCEL") } })
    }
}

@Composable
private fun TerminalNameDialog(title: String, initial: String, optional: Boolean, enabled: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    val error = runCatching { TmuxWindowCommands.validateName(name) }.exceptionOrNull()?.message
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        Column {
            OutlinedTextField(name, { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                label = { Text(if (optional) "Name (optional)" else "Terminal name") }, isError = error != null)
            if (optional) Text("Opens a new shell in this project's folder.", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MegaDriveWarning, style = MaterialTheme.typography.bodySmall) }
        }
    }, confirmButton = { TextButton(enabled = enabled && error == null && (optional || name.isNotBlank()), onClick = { onSave(name) }) { Text("SAVE") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } })
}
