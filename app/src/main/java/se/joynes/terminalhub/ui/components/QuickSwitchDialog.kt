package se.joynes.terminalhub.ui.components

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import se.joynes.terminalhub.data.settings.fuzzyShortcutScore
import se.joynes.terminalhub.ui.theme.*

internal data class QuickSwitchTarget(val id: String, val name: String, val detail: String)

@Composable
internal fun QuickSwitchDialog(title: String, targets: List<QuickSwitchTarget>, onChoose: (String) -> Unit,
    onDismiss: () -> Unit, onCreateProject: (() -> Unit)? = null) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableIntStateOf(0) }
    val results = remember(query, targets) {
        targets.mapNotNull { target -> fuzzyShortcutScore(query, target.name + " " + target.detail)?.let { target to it } }
            .sortedBy { it.second }.map { it.first }
    }
    val index = selected.coerceIn(0, (results.size - 1).coerceAtLeast(0))
    val focus = remember { FocusRequester() }
    val list = rememberLazyListState()
    LaunchedEffect(results, index) { if (results.isNotEmpty()) list.animateScrollToItem(index) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        WindowKeyHandler { event ->
            if (!isShortcutKeyboardEvent(event) || event.isCtrlPressed || event.isAltPressed || event.isMetaPressed ||
                event.keyCode !in setOf(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_ESCAPE)) false
            else {
                if (event.action == KeyEvent.ACTION_DOWN) when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> selected = (index + 1).coerceAtMost((results.size - 1).coerceAtLeast(0))
                    KeyEvent.KEYCODE_DPAD_UP -> selected = (index - 1).coerceAtLeast(0)
                    KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> if (event.repeatCount == 0) results.getOrNull(index)?.let { onChoose(it.id) }
                    KeyEvent.KEYCODE_ESCAPE -> if (event.repeatCount == 0) onDismiss()
                }
                true
            }
        }
        Column {
            OutlinedTextField(query, { query = it; selected = 0 }, label = { Text("Type part of a name") },
                singleLine = true, modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("quick-switch-query"))
            Text("↑ ↓ to choose · Enter to open · Escape to cancel", style = MaterialTheme.typography.bodySmall, color = MegaDriveDim)
            if (results.isEmpty()) Text("No matches", modifier = Modifier.padding(16.dp))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp).testTag("quick-switch-results"), state = list) {
                itemsIndexed(results, key = { _, item -> item.id }) { position, target ->
                    Column(Modifier.fillMaxWidth().background(if (position == index) MegaDrivePrimary.copy(alpha = .15f) else MegaDriveSurface)
                        .clickable { onChoose(target.id) }.semantics { this.selected = position == index }
                        .padding(12.dp)) {
                        Text(target.name, color = MegaDriveOnSurface)
                        Text(target.detail, color = MegaDriveDim, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        LaunchedEffect(Unit) { focus.requestFocus() }
    }, confirmButton = {
        TextButton(enabled = results.isNotEmpty(), onClick = { results.getOrNull(index)?.let { onChoose(it.id) } }) { Text("OPEN") }
    }, dismissButton = {
        Row {
            onCreateProject?.let { TextButton(onClick = it) { Text("NEW PROJECT") } }
            TextButton(onClick = onDismiss) { Text("CANCEL") }
        }
    })
}
