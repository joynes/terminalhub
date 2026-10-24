package se.joynes.terminalhub.ui.screen.sessions

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import se.joynes.terminalhub.data.db.entity.PinnedActionEntity
import se.joynes.terminalhub.data.model.PinnedMacroCodec
import se.joynes.terminalhub.data.model.pinnedActionName
import se.joynes.terminalhub.domain.MacroStep
import se.joynes.terminalhub.domain.TerminalMacro

private data class MacroStepDraft(val id: Long, val kind: String, val value: String,
    val ctrl: Boolean = false, val alt: Boolean = false, val shift: Boolean = false, val repeat: String = "1") {
    fun step(): MacroStep = when (kind) {
        "text" -> MacroStep.Text(value)
        "pause" -> MacroStep.Pause(value.toLongOrNull() ?: -1)
        else -> MacroStep.Key(value, ctrl, alt, shift, repeat.toIntOrNull() ?: 0)
    }
}

private fun MacroStep.draft(id: Long) = when (this) {
    is MacroStep.Text -> MacroStepDraft(id, "text", text)
    is MacroStep.Pause -> MacroStepDraft(id, "pause", milliseconds.toString())
    is MacroStep.Key -> MacroStepDraft(id, "key", key, ctrl, alt, shift, repeat.toString())
}

@Composable
internal fun PinnedActionEditor(initial: PinnedActionEntity, projectId: Long, onSave: (PinnedActionEntity) -> Unit, onDismiss: () -> Unit) {
    val decoded = remember(initial) { runCatching { if (initial.isMacro) PinnedMacroCodec.decode(initial.macroJson) else emptyList() } }
    var decodeError by remember { mutableStateOf(decoded.exceptionOrNull()?.message) }
    var name by remember { mutableStateOf(initial.name) }
    var text by remember { mutableStateOf(if (initial.isMacro) decoded.getOrDefault(emptyList()).filterIsInstance<MacroStep.Text>().joinToString("") { it.text } else initial.text) }
    var global by remember { mutableStateOf(initial.scope == "GLOBAL") }
    var sendEnter by remember { mutableStateOf(initial.sendEnter) }
    var macro by remember { mutableStateOf(initial.isMacro) }
    var nextId by remember { mutableLongStateOf(1000) }
    var drafts by remember { mutableStateOf(decoded.getOrDefault(emptyList()).mapIndexed { index, step -> step.draft(index.toLong()) }) }
    val listState = rememberLazyListState()
    var scrollToStep by remember { mutableStateOf<Long?>(null) }
    val steps = remember(drafts) { drafts.map { it.step() } }
    val validation = remember(steps) { runCatching { TerminalMacro.validate(steps) }.exceptionOrNull()?.message }
    val canSave = if (macro) validation == null && decodeError == null else text.isNotBlank()

    fun update(index: Int, value: MacroStepDraft) { drafts = drafts.toMutableList().apply { this[index] = value }; decodeError = null }
    fun move(from: Int, to: Int) { drafts = drafts.toMutableList().apply { add(to, removeAt(from)) } }
    fun add(step: MacroStep) {
        if (drafts.size < TerminalMacro.MAX_STEPS) {
            val draft = step.draft(nextId++)
            drafts = drafts + draft
            scrollToStep = draft.id
            decodeError = null
        }
    }
    LaunchedEffect(scrollToStep) {
        val index = drafts.indexOfFirst { it.id == scrollToStep }
        if (macro && index >= 0) listState.animateScrollToItem(index + 3)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.98f).fillMaxHeight(0.96f).imePadding().testTag("pinned-action-editor-dialog"), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(horizontal = 12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (initial.id == 0L) "Pin action" else "Edit action", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(enabled = canSave, onClick = {
                        val content = if (macro) TerminalMacro.summary(steps) else text
                        onSave(initial.copy(name = pinnedActionName(name, content), text = content, scope = if (global) "GLOBAL" else "PROJECT",
                            projectId = if (global) null else projectId, sendEnter = if (macro) false else sendEnter,
                            macroJson = if (macro) PinnedMacroCodec.encode(steps) else ""))
                    }) { Text("Save") }
                }
                LazyColumn(Modifier.weight(1f).testTag("pinned-action-editor"), state = listState, verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)) {
                    item {
                        OutlinedTextField(name, { name = it }, label = { Text("Name (optional)") }, modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(pinnedActionName("", if (macro) TerminalMacro.summary(steps) else text), maxLines = 1, overflow = TextOverflow.Ellipsis) }, singleLine = true)
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(!macro, { macro = false }, label = { Text("Text action") })
                            FilterChip(macro, {
                                if (drafts.isEmpty() && text.isNotBlank()) {
                                    drafts = (listOf(MacroStep.Text(text)) + if (sendEnter) listOf(MacroStep.Key("ENTER")) else emptyList())
                                        .map { it.draft(nextId++) }
                                }
                                macro = true
                            }, label = { Text("Keyboard macro") })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(!global, { global = false }, label = { Text("Project") })
                            FilterChip(global, { global = true }, label = { Text("Global") })
                        }
                    }
                    if (!macro) {
                        item { OutlinedTextField(text, { text = it }, label = { Text("Input") }, minLines = 2, maxLines = 5, modifier = Modifier.fillMaxWidth()) }
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(sendEnter, { sendEnter = it })
                                Text("Send immediately with Enter")
                            }
                            Text("Off: open in Text Input for editing.", style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        item {
                            Text("Steps run from top to bottom in the terminal where you start the macro. Text is pasted; keys are sent separately. Add pauses when a program needs time to react.", style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = {
                                drafts = TerminalMacro.interruptAndSend(text).map { it.draft(nextId++) }; decodeError = null
                            }) { Text("Template: Ctrl+C → text → Enter") }
                        }
                        itemsIndexed(drafts, key = { _, draft -> draft.id }) { index, draft ->
                            val stepError = remember(draft) { runCatching { TerminalMacro.validate(listOf(draft.step())) }.exceptionOrNull()?.message }
                            OutlinedCard(Modifier.fillMaxWidth().testTag("macro-step-${index + 1}")) {
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Text("${index + 1}. ${when (draft.kind) { "text" -> "Text"; "pause" -> "Pause"; else -> "Key" }}", Modifier.weight(1f))
                                        TextButton(onClick = { move(index, index - 1) }, enabled = index > 0,
                                            modifier = Modifier.semantics { contentDescription = "Move step ${index + 1} up" }) { Text("↑") }
                                        TextButton(onClick = { move(index, index + 1) }, enabled = index < drafts.lastIndex,
                                            modifier = Modifier.semantics { contentDescription = "Move step ${index + 1} down" }) { Text("↓") }
                                    }
                                    when (draft.kind) {
                                        "text" -> OutlinedTextField(draft.value, { update(index, draft.copy(value = it)) },
                                            label = { Text("Text for step ${index + 1}") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5)
                                        "pause" -> OutlinedTextField(draft.value, { update(index, draft.copy(value = it)) },
                                            label = { Text("Pause ${index + 1} (milliseconds)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
                                            modifier = Modifier.fillMaxWidth())
                                        else -> {
                                            OutlinedTextField(draft.value, { update(index, draft.copy(value = it)) },
                                                label = { Text("Key for step ${index + 1}") }, supportingText = { Text("One character, SPACE, ENTER, TAB, ESC, arrows, HOME, END, PAGE_UP/DOWN, INSERT, DELETE, BACKSPACE or F1–F12.") },
                                                modifier = Modifier.fillMaxWidth(), singleLine = true)
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                FilterChip(draft.ctrl, { update(index, draft.copy(ctrl = !draft.ctrl)) }, label = { Text("Ctrl") })
                                                FilterChip(draft.alt, { update(index, draft.copy(alt = !draft.alt)) }, label = { Text("Alt") })
                                                FilterChip(draft.shift, { update(index, draft.copy(shift = !draft.shift)) }, label = { Text("Shift") })
                                            }
                                            OutlinedTextField(draft.repeat, { update(index, draft.copy(repeat = it)) }, label = { Text("Repeat step ${index + 1}") },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                                        }
                                    }
                                    stepError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                                        TextButton(onClick = { drafts = drafts.toMutableList().apply { add(index + 1, draft.copy(id = nextId++)) } }, enabled = drafts.size < TerminalMacro.MAX_STEPS) { Text("Duplicate") }
                                        TextButton(onClick = { drafts = drafts.filterNot { it.id == draft.id } },
                                            modifier = Modifier.semantics { contentDescription = "Delete step ${index + 1}" }) { Text("Delete") }
                                    }
                                }
                            }
                        }
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { add(MacroStep.Key("ENTER")) }, enabled = drafts.size < TerminalMacro.MAX_STEPS) { Text("+ Key") }
                                TextButton(onClick = { add(MacroStep.Text("")) }, enabled = drafts.size < TerminalMacro.MAX_STEPS) { Text("+ Text") }
                                TextButton(onClick = { add(MacroStep.Pause(300)) }, enabled = drafts.size < TerminalMacro.MAX_STEPS) { Text("+ Pause") }
                            }
                            (decodeError ?: validation)?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        }
    }
}
