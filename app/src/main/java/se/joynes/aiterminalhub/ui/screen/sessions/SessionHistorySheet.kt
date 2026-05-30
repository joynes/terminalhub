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
    onReopen: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); tick++ } }

    val sorted = sessions.sortedByDescending { it.lastOpenedAt }.also { tick.let {} }
    val sortedClosed = closedSessions.sortedByDescending { it.lastOpenedAt }.also { tick.let {} }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MegaDriveSurface,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                "RECENT PROJECTS",
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
                        SessionHistoryItem(
                            meta = meta,
                            isActive = meta.id == activeId,
                            onSelect = { onSelect(meta.id); onDismiss() }
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
                            selected = meta.projectId in selectedIds,
                            onToggle = {
                                selectedIds = if (meta.projectId in selectedIds)
                                    selectedIds - meta.projectId
                                else
                                    selectedIds + meta.projectId
                            },
                            onReopen = { onReopen(meta.projectId); onDismiss() }
                        )
                        HorizontalDivider(color = MegaDriveDim.copy(alpha = 0.3f))
                    }
                }
            }
            if (selectedIds.isNotEmpty()) {
                se.joynes.aiterminalhub.ui.components.RetroButton(
                    text = "↩ REOPEN SELECTED (${selectedIds.size})",
                    onClick = {
                        selectedIds.forEach { onReopen(it) }
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SessionHistoryItem(
    meta: TerminalSessionMeta,
    isActive: Boolean,
    onSelect: () -> Unit
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
    }
}

@Composable
private fun ClosedSessionItem(
    meta: TerminalSessionMeta,
    selected: Boolean,
    onToggle: () -> Unit,
    onReopen: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) MegaDriveBg else MegaDriveSurface)
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = MegaDrivePrimary,
                uncheckedColor = MegaDriveDim,
                checkmarkColor = MegaDriveBg
            ),
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                meta.projectName.uppercase(),
                fontFamily = MonoFontFamily,
                color = if (selected) MegaDrivePrimary else MegaDriveDim,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "last opened ${formatRelativeTime(meta.lastOpenedAt)}",
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
