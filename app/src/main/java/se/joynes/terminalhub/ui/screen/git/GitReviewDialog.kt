package se.joynes.terminalhub.ui.screen.git

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import se.joynes.terminalhub.data.git.*
import se.joynes.terminalhub.ui.theme.MonoFontFamily

@Composable
fun GitReviewDialog(
    state: GitReviewState,
    onRepository: (String) -> Unit,
    onMode: (GitReviewMode) -> Unit,
    onFile: (GitChangedFile) -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    var fullScreen by rememberSaveable { mutableStateOf(true) }
    var fontSize by rememberSaveable { mutableIntStateOf(14) }
    Dialog(
        onDismissRequest = { if (state.file != null) onBack() else onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = CodePalette.background,
            contentColor = CodePalette.foreground,
            modifier = (if (fullScreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.85f))
                .testTag(if (fullScreen) "git-fullscreen" else "git-windowed")
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Git · ${state.projectName}", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = { fullScreen = !fullScreen }) { Text(if (fullScreen) "WINDOW" else "FULL SCREEN") }
                    TextButton(onClick = onDismiss) { Text("CLOSE") }
                }
                if (state.file == null) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(state.snapshot?.let { "Branch: ${it.branch}" } ?: "Git repositories",
                            modifier = Modifier.weight(1f), color = CodePalette.secondary)
                        TextButton(onClick = onRefresh, enabled = !state.loading) { Text("REFRESH") }
                    }
                    state.repository?.let { repo ->
                        Text(repo, color = CodePalette.secondary, fontSize = 12.sp, maxLines = 2)
                        if (state.repositories.size > 1) {
                            Row(Modifier.horizontalScroll(rememberScrollState())) {
                                state.repositories.forEach { candidate ->
                                    FilterChip(selected = candidate == repo, onClick = { onRepository(candidate) },
                                        label = { Text(candidate.substringAfterLast('/')) }, modifier = Modifier.padding(end = 6.dp))
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            GitReviewMode.entries.forEach { mode ->
                                FilterChip(selected = mode == state.mode, onClick = { onMode(mode) }, label = { Text(mode.label) })
                            }
                        }
                        Text(state.mode.explanation, color = CodePalette.secondary, fontSize = 13.sp, lineHeight = 18.sp)
                        if (state.mode == GitReviewMode.COMMIT) {
                            state.snapshot?.let { Text(it.commit, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp)) }
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onBack) { Text("‹ FILES") }
                        Text(state.mode.label, Modifier.weight(1f), color = CodePalette.secondary, fontSize = 13.sp)
                        TextButton(onClick = { fontSize = (fontSize - 1).coerceAtLeast(11) }, enabled = fontSize > 11) { Text("A−") }
                        TextButton(onClick = { fontSize = (fontSize + 1).coerceAtMost(22) }, enabled = fontSize < 22) { Text("A+") }
                    }
                    Text(state.file.path, fontFamily = MonoFontFamily, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
                }
                if (state.loading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().testTag("git-loading"))
                    Text("Reading from server…", color = CodePalette.secondary, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
                }
                state.error?.let { error ->
                    Text(error, color = CodePalette.removedMarker, modifier = Modifier.padding(vertical = 12.dp))
                    TextButton(onClick = { if (state.file != null) onFile(state.file) else onRefresh() }) { Text("RETRY") }
                }
                when {
                    state.patch != null -> key(state.repository, state.mode, state.file) {
                        GitDiffContent(state.patch, state.file?.path.orEmpty(), fontSize, state.truncated, Modifier.weight(1f))
                    }
                    state.file == null && state.snapshot != null -> {
                        val listState = rememberLazyListState()
                        if (state.snapshot.files.isEmpty() && !state.loading) {
                            Text(if (state.mode == GitReviewMode.COMMIT) "No file changes in this commit." else "No changes in this view.",
                                color = CodePalette.secondary, modifier = Modifier.padding(vertical = 24.dp))
                        }
                        LazyColumn(Modifier.weight(1f).testTag("git-file-list"), state = listState) {
                            items(state.snapshot.files, key = { it.path }) { file ->
                                Column(Modifier.fillMaxWidth().clickable { onFile(file) }.padding(vertical = 14.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text(when { file.isUntracked -> "NEW"; file.status.startsWith("A") -> "+"; file.status.startsWith("D") -> "−"; else -> file.status },
                                            color = if (file.status.startsWith("D")) CodePalette.removedMarker else CodePalette.addedMarker,
                                            fontFamily = MonoFontFamily, modifier = Modifier.widthIn(min = 30.dp))
                                        Text(file.path, fontFamily = MonoFontFamily, fontSize = 14.sp)
                                    }
                                    file.previousPath?.let { Text("From: $it", color = CodePalette.secondary, fontSize = 12.sp) }
                                }
                                HorizontalDivider(color = CodePalette.secondary.copy(alpha = 0.2f))
                            }
                        }
                    }
                    !state.loading && state.file == null && state.error == null && state.repository == null -> {
                        if (state.repositories.isEmpty()) {
                            Text("No Git repository found in this project folder or its parent folders.", modifier = Modifier.padding(vertical = 24.dp))
                        } else {
                            Text("Choose a repository", modifier = Modifier.padding(vertical = 12.dp))
                            LazyColumn(Modifier.weight(1f)) {
                                items(state.repositories) { repo ->
                                    Text(repo, Modifier.fillMaxWidth().clickable { onRepository(repo) }.padding(vertical = 16.dp), fontFamily = MonoFontFamily)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun GitDiffContent(patch: String, fileName: String, fontSize: Int, truncated: Boolean, modifier: Modifier = Modifier) {
    val allLines = remember(patch) { parseGitDiff(patch, maxLines = 6001) }
    val lines = remember(allLines) { allLines.take(6000) }
    val highlighted = remember(lines, fileName) { highlightDiffLines(lines, fileName) }
    val listState = rememberLazyListState()
    Column(modifier.fillMaxWidth()) {
        Text("− removed    + added    old / new line", color = CodePalette.secondary, fontSize = 12.sp,
            modifier = Modifier.padding(vertical = 6.dp))
        if (truncated || allLines.size > lines.size) {
            Text("Large diff: showing the beginning only.", color = CodePalette.addedMarker, fontSize = 13.sp)
        }
        if (lines.isEmpty()) {
            Text("No text differences. The file may have changed since the list was loaded.", modifier = Modifier.padding(16.dp))
        }
        SelectionContainer(Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize().testTag("git-diff-scroll"), state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
                itemsIndexed(lines) { index, line ->
                    val background = when (line.kind) {
                        DiffLineKind.ADDED -> CodePalette.added
                        DiffLineKind.REMOVED -> CodePalette.removed
                        DiffLineKind.HUNK -> androidx.compose.ui.graphics.Color(0xFF213249)
                        else -> CodePalette.background
                    }
                    val marker = when (line.kind) { DiffLineKind.ADDED -> "+"; DiffLineKind.REMOVED -> "−"; else -> " " }
                    val code = line.kind in setOf(DiffLineKind.ADDED, DiffLineKind.REMOVED, DiffLineKind.CONTEXT)
                    val shortened = line.text.take(16_000)
                    val formatted = highlighted[index]
                    Row(Modifier.fillMaxWidth().background(background).padding(vertical = 3.dp).testTag("git-diff-line-$index")) {
                        if (code) {
                            Text("${line.oldLine ?: ""}", color = CodePalette.secondary,
                                fontFamily = MonoFontFamily, fontSize = 10.sp, lineHeight = (fontSize * 1.5f).sp,
                                modifier = Modifier.width(32.dp).padding(start = 2.dp))
                            Text("${line.newLine ?: ""}", color = CodePalette.secondary,
                                fontFamily = MonoFontFamily, fontSize = 10.sp, lineHeight = (fontSize * 1.5f).sp,
                                modifier = Modifier.width(32.dp).padding(start = 2.dp))
                            Text(marker, color = if (line.kind == DiffLineKind.REMOVED) CodePalette.removedMarker else CodePalette.addedMarker,
                                fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold, fontSize = fontSize.sp,
                                modifier = Modifier.width(16.dp))
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                            Text(formatted, color = if (code) CodePalette.foreground else CodePalette.secondary,
                                fontFamily = MonoFontFamily, fontSize = (if (code) fontSize else 12).sp,
                                lineHeight = (if (code) fontSize * 1.5f else 18f).sp, softWrap = true)
                            if (shortened.length < line.text.length) Text("[Long line shortened]", color = CodePalette.secondary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
