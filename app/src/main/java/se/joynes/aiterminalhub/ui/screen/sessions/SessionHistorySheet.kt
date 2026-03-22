package se.joynes.aiterminalhub.ui.screen.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.joynes.aiterminalhub.domain.TerminalSessionId
import se.joynes.aiterminalhub.domain.TerminalSessionMeta
import se.joynes.aiterminalhub.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionHistorySheet(
    sessions: List<TerminalSessionMeta>,
    closedSessions: List<TerminalSessionMeta>,
    activeId: TerminalSessionId?,
    onSelect: (TerminalSessionId) -> Unit,
    onClose: (TerminalSessionId) -> Unit,
    onReopen: (Long) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); tick++ } }

    val sorted = sessions.sortedByDescending { it.lastOpenedAt }.also { tick.let {} }
    val sortedClosed = closedSessions.sortedByDescending { it.lastOpenedAt }.also { tick.let {} }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MegaDriveSurface,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                "SESSIONS",
                fontFamily = MonoFontFamily,
                color = MegaDrivePrimary,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            HorizontalDivider(color = MegaDriveDim)

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
            ) {
                if (sorted.isEmpty() && sortedClosed.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("NO SESSIONS", fontFamily = MonoFontFamily, color = MegaDriveDim, fontSize = 11.sp)
                        }
                    }
                }

                // Active sessions
                if (sorted.isNotEmpty()) {
                    itemsIndexed(sorted) { _, meta ->
                        val tabIndex = sessions.indexOf(meta)
                        SessionHistoryItem(
                            meta = meta,
                            isActive = meta.id == activeId,
                            tabIndex = tabIndex,
                            tabCount = sessions.size,
                            onSelect = { onSelect(meta.id); onDismiss() },
                            onClose = { onClose(meta.id) },
                            onMoveUp = { if (tabIndex > 0) onMoveUp(tabIndex) },
                            onMoveDown = { if (tabIndex < sessions.size - 1) onMoveDown(tabIndex) }
                        )
                        HorizontalDivider(color = MegaDriveDim.copy(alpha = 0.4f))
                    }
                }

                // Closed sessions section
                if (sortedClosed.isNotEmpty()) {
                    item {
                        Text(
                            "CLOSED",
                            fontFamily = MonoFontFamily,
                            color = MegaDriveDim,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                        HorizontalDivider(color = MegaDriveDim.copy(alpha = 0.4f))
                    }
                    items(sortedClosed) { meta ->
                        ClosedSessionItem(
                            meta = meta,
                            onReopen = { onReopen(meta.projectId); onDismiss() }
                        )
                        HorizontalDivider(color = MegaDriveDim.copy(alpha = 0.3f))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SessionHistoryItem(
    meta: TerminalSessionMeta,
    isActive: Boolean,
    tabIndex: Int,
    tabCount: Int,
    onSelect: () -> Unit,
    onClose: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isActive) MegaDriveBg else MegaDriveSurface)
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("●", color = if (meta.isConnected) MegaDriveGreen else MegaDriveError, fontSize = 10.sp, fontFamily = MonoFontFamily)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                meta.projectName.uppercase(),
                fontFamily = MonoFontFamily,
                color = if (isActive) MegaDrivePrimary else MegaDriveOnSurface,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "last opened ${formatRelativeTime(meta.lastOpenedAt)}",
                fontFamily = MonoFontFamily,
                color = MegaDriveDim.copy(alpha = 0.8f),
                fontSize = 9.sp
            )
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text("▲", color = if (tabIndex > 0) MegaDrivePrimary else MegaDriveDim, fontSize = 10.sp, fontFamily = MonoFontFamily,
                modifier = Modifier.clickable(enabled = tabIndex > 0, onClick = onMoveUp))
            Text("▼", color = if (tabIndex < tabCount - 1) MegaDrivePrimary else MegaDriveDim, fontSize = 10.sp, fontFamily = MonoFontFamily,
                modifier = Modifier.clickable(enabled = tabIndex < tabCount - 1, onClick = onMoveDown))
        }
        Spacer(Modifier.width(12.dp))
        Text("✕", color = MegaDriveAccent, fontSize = 12.sp, fontFamily = MonoFontFamily,
            modifier = Modifier.clickable(onClick = onClose))
    }
}

@Composable
private fun ClosedSessionItem(
    meta: TerminalSessionMeta,
    onReopen: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MegaDriveSurface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("○", color = MegaDriveDim, fontSize = 10.sp, fontFamily = MonoFontFamily)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                meta.projectName.uppercase(),
                fontFamily = MonoFontFamily,
                color = MegaDriveDim,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "closed ${formatRelativeTime(meta.lastOpenedAt)}",
                fontFamily = MonoFontFamily,
                color = MegaDriveDim.copy(alpha = 0.6f),
                fontSize = 9.sp
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "↩ OPEN",
            fontFamily = MonoFontFamily,
            color = MegaDrivePrimary,
            fontSize = 10.sp,
            modifier = Modifier.clickable(onClick = onReopen)
        )
    }
}

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
private val dateFormat = SimpleDateFormat("dd MMM", Locale.getDefault())

private fun formatRelativeTime(epochMs: Long): String {
    val diffMs = System.currentTimeMillis() - epochMs
    return when {
        diffMs < 60_000 -> "just now"
        diffMs < 3_600_000 -> "${diffMs / 60_000}m ago"
        diffMs < 86_400_000 -> timeFormat.format(Date(epochMs))
        else -> dateFormat.format(Date(epochMs))
    }
}
