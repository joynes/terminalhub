package se.joynes.terminalhub.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import se.joynes.terminalhub.domain.TerminalSessionId
import se.joynes.terminalhub.ui.screen.sessions.ProjectTabState
import se.joynes.terminalhub.ui.theme.*

private const val TAB_WIDTH_DP = 70

/** Deterministic hue from seed → dark background colour that reads well against light text. */
private fun tabColor(seed: Int, active: Boolean): Color {
    val hue = ((seed.toLong() and 0x7FFFFFFF) % 360).toFloat()
    return if (active)
        Color.hsl(hue, saturation = 0.60f, lightness = 0.30f)
    else
        Color.hsl(hue, saturation = 0.30f, lightness = 0.12f)
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun SessionTabBar(
    tabs: List<ProjectTabState>,
    activeId: TerminalSessionId?,
    onSelect: (Long) -> Unit,
    onClose: (Long, TerminalSessionId?) -> Unit,
    onRestartTmux: (Long) -> Unit = {},
    onMove: (Int, Int) -> Unit,
    onAddProject: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current
    var menuTabId by remember { mutableStateOf<Long?>(null) }
    var draggedTabId by remember { mutableStateOf<Long?>(null) }
    var dragPointer by remember { mutableStateOf(Offset.Zero) }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    val tabBounds = remember { mutableStateMapOf<Long, Rect>() }
    val latestTabs by rememberUpdatedState(tabs)
    val latestOnMove by rememberUpdatedState(onMove)

    LaunchedEffect(tabs.map { it.projectId }) {
        val currentIds = tabs.mapTo(mutableSetOf()) { it.projectId }
        tabBounds.keys.filterNot { it in currentIds }.forEach(tabBounds::remove)
    }

    FlowRow(
        modifier = modifier
            .background(MegaDriveSurface)
            .heightIn(min = 28.dp),
        verticalArrangement = Arrangement.Top,
        horizontalArrangement = Arrangement.Start
    ) {
        tabs.forEachIndexed { index, tab ->
            val isSelected = tab.sessionId != null && tab.sessionId == activeId
            val bg = tabColor(tab.colorSeed, isSelected)
            val textColor = when {
                isSelected      -> Color.White
                tab.isConnecting -> MegaDrivePrimary
                tab.isConnected -> Color.White.copy(alpha = 0.65f)
                else            -> Color.White.copy(alpha = 0.28f)
            }
            key(tab.projectId) {
            Box(
                modifier = Modifier
                    .onGloballyPositioned { coordinates ->
                        tabBounds[tab.projectId] = coordinates.boundsInParent()
                    }
                    .zIndex(if (draggedTabId == tab.projectId) 2f else 0f)
            ) {
                Row(
                    modifier = Modifier
                        .width(TAB_WIDTH_DP.dp)
                        .height(28.dp)
                        .background(bg)
                        .then(
                            if (draggedTabId == tab.projectId) {
                                Modifier.border(2.dp, MegaDriveAccent)
                            } else {
                                Modifier
                            }
                        )
                        .clickable { onSelect(tab.projectId) }
                        .pointerInput(tab.projectId) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { localStart ->
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    menuTabId = null
                                    draggedTabId = tab.projectId
                                    dragDistance = 0f
                                    dragPointer = (tabBounds[tab.projectId]?.topLeft ?: Offset.Zero) + localStart
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragPointer += dragAmount
                                    dragDistance += dragAmount.getDistance()
                                    if (dragDistance >= viewConfiguration.touchSlop) {
                                        val currentTabs = latestTabs
                                        val fromIndex = currentTabs.indexOfFirst { it.projectId == tab.projectId }
                                        val toIndex = tabDropTargetIndex(
                                            tabIds = currentTabs.map { it.projectId },
                                            bounds = tabBounds,
                                            pointer = dragPointer
                                        )
                                        if (fromIndex >= 0 && toIndex != null && toIndex != fromIndex) {
                                            latestOnMove(fromIndex, toIndex)
                                        }
                                    }
                                },
                                onDragEnd = {
                                    if (dragDistance < viewConfiguration.touchSlop) {
                                        menuTabId = tab.projectId
                                    }
                                    draggedTabId = null
                                    dragDistance = 0f
                                },
                                onDragCancel = {
                                    draggedTabId = null
                                    dragDistance = 0f
                                }
                            )
                        }
                        .padding(start = 7.dp, end = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tab.projectName.uppercase(),
                        color = textColor,
                        fontSize = 9.sp,
                        fontFamily = MonoFontFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                DropdownMenu(
                    expanded = menuTabId == tab.projectId,
                    onDismissRequest = { menuTabId = null }
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Restart tmux…",
                                color = if (tab.usesTmux && !tab.isConnecting) Color.White else MegaDriveDim,
                                fontFamily = MonoFontFamily,
                                fontSize = 12.sp
                            )
                        },
                        enabled = tab.usesTmux && !tab.isConnecting,
                        onClick = {
                            menuTabId = null
                            onRestartTmux(tab.projectId)
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text("Close", color = Color.White, fontFamily = MonoFontFamily, fontSize = 12.sp)
                        },
                        onClick = {
                            menuTabId = null
                            onClose(tab.projectId, tab.sessionId)
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Move Left",
                                color = if (index > 0) Color.White else MegaDriveDim,
                                fontFamily = MonoFontFamily,
                                fontSize = 12.sp
                            )
                        },
                        onClick = {
                            menuTabId = null
                            if (index > 0) onMove(index, index - 1)
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Move Right",
                                color = if (index < tabs.lastIndex) Color.White else MegaDriveDim,
                                fontFamily = MonoFontFamily,
                                fontSize = 12.sp
                            )
                        },
                        onClick = {
                            menuTabId = null
                            if (index < tabs.lastIndex) onMove(index, index + 1)
                        }
                    )
                }
            }
            }
        }
        Box(
            modifier = Modifier
                .height(28.dp)
                .clickable { onAddProject() }
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("+", color = MegaDriveAccent, fontSize = 14.sp)
        }
    }
}

internal fun tabDropTargetIndex(
    tabIds: List<Long>,
    bounds: Map<Long, Rect>,
    pointer: Offset
): Int? = tabIds
    .mapIndexedNotNull { index, projectId ->
        bounds[projectId]?.let { rect ->
            val dx = pointer.x - rect.center.x
            val dy = pointer.y - rect.center.y
            index to (dx * dx + dy * dy)
        }
    }
    .minByOrNull { it.second }
    ?.first
