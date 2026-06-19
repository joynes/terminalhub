package se.joynes.aiterminal.ui.screen.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termux.view.TerminalView
import se.joynes.aiterminal.ui.theme.*

data class SearchMatch(val row: Int)

@Composable
fun TerminalSearchOverlay(
    initialQuery: String,
    terminalViewRef: TerminalView?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember(initialQuery) { mutableStateOf(initialQuery) }
    var matches by remember { mutableStateOf<List<SearchMatch>>(emptyList()) }
    var currentIndex by remember { mutableStateOf(0) }
    val focusRequester = remember { FocusRequester() }

    fun updateHighlight(q: String, matchRow: Int) {
        terminalViewRef?.setSearchHighlight(q.trim(), matchRow)
    }

    fun runSearch(q: String) {
        val tv = terminalViewRef ?: return
        val emulator = tv.mEmulator ?: return
        val screen = emulator.getScreen()
        val transcriptRows = screen.activeTranscriptRows
        val cols = emulator.mColumns
        val rows = emulator.mRows
        val found = mutableListOf<SearchMatch>()
        val term = q.trim()
        if (term.isNotEmpty()) {
            // Read each row exactly once (selY1 == selY2 reads one row).
            // Also probe row + next row to catch terms that wrap a line boundary.
            for (row in -transcriptRows until rows) {
                val current = screen.getSelectedText(0, row, cols, row) ?: ""
                if (current.contains(term, ignoreCase = true)) {
                    found.add(SearchMatch(row))
                    continue
                }
                if (row + 1 < rows) {
                    val next = screen.getSelectedText(0, row + 1, cols, row + 1) ?: ""
                    if ((current + next).contains(term, ignoreCase = true)) {
                        found.add(SearchMatch(row))
                    }
                }
            }
        }
        matches = found
        // Default to the FIRST (highest-up) match so the user sees the top-most hit.
        currentIndex = 0
        updateHighlight(q, found.firstOrNull()?.row ?: Int.MIN_VALUE)
    }

    fun scrollToMatch(index: Int) {
        val tv = terminalViewRef ?: return
        val emulator = tv.mEmulator ?: return
        val match = matches.getOrNull(index) ?: return
        tv.scrollToRow(match.row - emulator.mRows / 2)
        updateHighlight(query, match.row)
    }

    DisposableEffect(Unit) {
        onDispose { terminalViewRef?.setSearchHighlight(null, Int.MIN_VALUE) }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        if (initialQuery.isNotEmpty()) runSearch(initialQuery)
    }

    LaunchedEffect(currentIndex, matches) {
        scrollToMatch(currentIndex)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(MegaDriveSurface)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(4.dp))
                .background(MegaDriveBg)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            if (query.isEmpty()) {
                Text("Search...", color = MegaDriveDim, fontSize = 13.sp, fontFamily = MonoFontFamily)
            }
            BasicTextField(
                value = query,
                onValueChange = { q ->
                    query = q
                    runSearch(q)
                },
                singleLine = true,
                textStyle = TextStyle(
                    color = MegaDrivePrimary,
                    fontSize = 13.sp,
                    fontFamily = MonoFontFamily
                ),
                cursorBrush = SolidColor(MegaDrivePrimary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    if (matches.isNotEmpty()) currentIndex = (currentIndex + 1) % matches.size
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
        }

        val matchLabel = when {
            query.trim().isEmpty() -> ""
            matches.isEmpty() -> "0"
            else -> "${currentIndex + 1}/${matches.size}"
        }
        if (matchLabel.isNotEmpty()) {
            Text(
                matchLabel,
                color = if (matches.isEmpty()) MegaDriveError else MegaDrivePrimary,
                fontSize = 11.sp,
                fontFamily = MonoFontFamily,
                modifier = Modifier.widthIn(min = 36.dp)
            )
        }

        SearchNavBtn("▲") {
            if (matches.isNotEmpty()) currentIndex = if (currentIndex == 0) matches.size - 1 else currentIndex - 1
        }
        SearchNavBtn("▼") {
            if (matches.isNotEmpty()) currentIndex = (currentIndex + 1) % matches.size
        }
        SearchNavBtn("✕", onClick = onDismiss)
    }
}

@Composable
private fun SearchNavBtn(label: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MegaDriveDim)
            .clickable(onClick = onClick)
    ) {
        Text(label, color = MegaDrivePrimary, fontSize = 12.sp, fontFamily = MonoFontFamily)
    }
}
