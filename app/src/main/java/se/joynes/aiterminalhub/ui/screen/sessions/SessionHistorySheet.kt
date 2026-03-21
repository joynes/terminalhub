package se.joynes.aiterminalhub.ui.screen.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
    activeId: TerminalSessionId?,
    onSelect: (TerminalSessionId) -> Unit,
    onClose: (TerminalSessionId) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    // Show sessions sorted by lastOpenedAt descending (most recent first)
    val sorted = sessions.sortedByDescending { it.lastOpenedAt }

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

            if (sorted.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "NO SESSIONS",
                        fontFamily = MonoFontFamily,
                        color = MegaDriveDim,
                        fontSize = 11.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                ) {
                    itemsIndexed(sorted) { index, meta ->
                        // Find tab bar index (position in unsorted sessions list)
                        val tabIndex = sessions.indexOf(meta)
                        val isActive = meta.id == activeId

                        SessionHistoryItem(
                            meta = meta,
                            isActive = isActive,
                            tabIndex = tabIndex,
                            tabCount = sessions.size,
                            onSelect = {
                                onSelect(meta.id)
                                onDismiss()
                            },
                            onClose = { onClose(meta.id) },
                            onMoveUp = { if (tabIndex > 0) onMoveUp(tabIndex) },
                            onMoveDown = { if (tabIndex < sessions.size - 1) onMoveDown(tabIndex) }
                        )
                        HorizontalDivider(color = MegaDriveDim.copy(alpha = 0.4f))
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
    val bg = if (isActive) MegaDriveBg else MegaDriveSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status dot
        val dotColor = if (meta.isConnected) MegaDriveGreen else MegaDriveError
        Text("●", color = dotColor, fontSize = 10.sp, fontFamily = MonoFontFamily)
        Spacer(Modifier.width(8.dp))

        // Project name + last opened
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

        // Reorder buttons
        Column {
            Text(
                "▲",
                color = if (tabIndex > 0) MegaDrivePrimary else MegaDriveDim,
                fontSize = 10.sp,
                fontFamily = MonoFontFamily,
                modifier = Modifier.clickable(enabled = tabIndex > 0, onClick = onMoveUp)
            )
            Text(
                "▼",
                color = if (tabIndex < tabCount - 1) MegaDrivePrimary else MegaDriveDim,
                fontSize = 10.sp,
                fontFamily = MonoFontFamily,
                modifier = Modifier.clickable(enabled = tabIndex < tabCount - 1, onClick = onMoveDown)
            )
        }

        Spacer(Modifier.width(12.dp))

        // Close button
        Text(
            "✕",
            color = MegaDriveAccent,
            fontSize = 12.sp,
            fontFamily = MonoFontFamily,
            modifier = Modifier.clickable(onClick = onClose)
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
