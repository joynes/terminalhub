package se.joynes.terminalhub.ui.components

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.key.onPreInterceptKeyBeforeSoftKeyboard
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
@OptIn(ExperimentalComposeUiApi::class)
internal fun QuickSwitchDialog(title: String, targets: List<QuickSwitchTarget>, onChoose: (String) -> Unit,
    onDismiss: () -> Unit, onCreateProject: (() -> Unit)? = null,
    includeClosed: Boolean = false, onIncludeClosedChange: ((Boolean) -> Unit)? = null, status: String? = null) {
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
            val navigationKey = when {
                event.isAltPressed || event.isMetaPressed || event.isShiftPressed -> null
                event.isCtrlPressed -> when (event.keyCode) {
                    KeyEvent.KEYCODE_N -> KeyEvent.KEYCODE_DPAD_DOWN
                    KeyEvent.KEYCODE_P -> KeyEvent.KEYCODE_DPAD_UP
                    else -> null
                }
                else -> event.keyCode.takeIf { it in setOf(KeyEvent.KEYCODE_DPAD_DOWN,
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_ESCAPE) }
            }
            if (!isShortcutKeyboardEvent(event) || navigationKey == null) false
            else {
                if (event.action == KeyEvent.ACTION_DOWN) when (navigationKey) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> selected = (index + 1).coerceAtMost((results.size - 1).coerceAtLeast(0))
                    KeyEvent.KEYCODE_DPAD_UP -> selected = (index - 1).coerceAtLeast(0)
                    KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> if (event.repeatCount == 0) results.getOrNull(index)?.let { onChoose(it.id) }
                    KeyEvent.KEYCODE_ESCAPE -> if (event.repeatCount == 0) onDismiss()
                }
                true
            }
        }
        Column {
            onIncludeClosedChange?.let { change ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = includeClosed, onCheckedChange = change)
                    Text("Include closed projects", modifier = Modifier.clickable { change(!includeClosed) })
                }
            }
            status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MegaDriveDim) }
            OutlinedTextField(query, { query = it; selected = 0 }, label = { Text("Type part of a name") },
                singleLine = true, modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("quick-switch-query")
                    .onPreInterceptKeyBeforeSoftKeyboard { key ->
                        val event = key.nativeKeyEvent
                        if (event.keyCode == KeyEvent.KEYCODE_ESCAPE && !event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed) {
                            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) onDismiss()
                            true
                        } else false
                    })
            Text("↑ ↓ / Ctrl+P Ctrl+N to choose · Enter to open · Escape to cancel", style = MaterialTheme.typography.bodySmall, color = MegaDriveDim)
            if (results.isEmpty()) Text("No matches", modifier = Modifier.padding(16.dp))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp).testTag("quick-switch-results"), state = list) {
                itemsIndexed(results, key = { _, item -> item.id }) { position, target ->
                    val isWindow = target.id.startsWith("window:")
                    val typeColor = if (isWindow) Color(0xFFC6B5FF) else MegaDrivePrimary
                    val isCurrent = position == index
                    Column(Modifier.fillMaxWidth()
                        .background(typeColor.copy(alpha = if (isCurrent) .16f else .05f))
                        .border(if (isCurrent) 2.dp else 0.dp, if (isCurrent) typeColor else Color.Transparent)
                        .clickable { onChoose(target.id) }.semantics { this.selected = position == index }
                        .padding(12.dp)) {
                        Text(if (isWindow) "WINDOW" else "PROJECT", color = typeColor,
                            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
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
