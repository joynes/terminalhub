package se.joynes.terminalhub.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.graphics.graphicsLayer
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
    onReorder: (List<Long>) -> Unit,
    onAddProject: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current
    var menuTabId by remember { mutableStateOf<Long?>(null) }
    var reorderMode by remember { mutableStateOf(false) }
    var draftOrder by remember { mutableStateOf<List<Long>>(emptyList()) }
    var draggedTabId by remember { mutableStateOf<Long?>(null) }
    var dropTargetTabId by remember { mutableStateOf<Long?>(null) }
    var dragPointer by remember { mutableStateOf(Offset.Zero) }
    var dragTranslation by remember { mutableStateOf(Offset.Zero) }
    val tabBounds = remember { mutableStateMapOf<Long, Rect>() }
    val displayedTabs = if (reorderMode) {
        val tabById = tabs.associateBy { it.projectId }
        val ordered = draftOrder.mapNotNull(tabById::get)
        ordered + tabs.filterNot { it.projectId in draftOrder }
    } else {
        tabs
    }
    val latestDisplayedTabs by rememberUpdatedState(displayedTabs)
    val latestOnReorder by rememberUpdatedState(onReorder)

    LaunchedEffect(tabs.map { it.projectId }) {
        val currentIds = tabs.mapTo(mutableSetOf()) { it.projectId }
        tabBounds.keys.filterNot { it in currentIds }.forEach(tabBounds::remove)
        if (reorderMode) {
            draftOrder = draftOrder.filter { it in currentIds } +
                tabs.map { it.projectId }.filterNot { it in draftOrder }
        }
    }

    Column(
        modifier = modifier
            .background(MegaDriveSurface)
            .heightIn(min = 28.dp)
    ) {
        if (reorderMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .background(MegaDriveAccent)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "PREVIEW — DRAG TABS FREELY",
                    color = MegaDriveBg,
                    fontFamily = MonoFontFamily,
                    fontSize = 9.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "CANCEL",
                    color = MegaDriveBg,
                    fontFamily = MonoFontFamily,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .clickable {
                            reorderMode = false
                            draftOrder = emptyList()
                            draggedTabId = null
                            dropTargetTabId = null
                            dragTranslation = Offset.Zero
                        }
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                )
                Text(
                    "DONE",
                    color = MegaDriveBg,
                    fontFamily = MonoFontFamily,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clickable {
                            latestOnReorder(draftOrder)
                            reorderMode = false
                            draftOrder = emptyList()
                            draggedTabId = null
                            dropTargetTabId = null
                            dragTranslation = Offset.Zero
                        }
                        .padding(start = 6.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)
                )
            }
        }
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 28.dp),
            verticalArrangement = Arrangement.Top,
            horizontalArrangement = Arrangement.Start
        ) {
            displayedTabs.forEach { tab ->
                val isSelected = tab.sessionId != null && tab.sessionId == activeId
                val bg = tabColor(tab.colorSeed, isSelected)
                val textColor = when {
                    isSelected -> Color.White
                    tab.isConnecting -> MegaDrivePrimary
                    tab.isConnected -> Color.White.copy(alpha = 0.65f)
                    else -> Color.White.copy(alpha = 0.28f)
                }
                key(tab.projectId) {
                    Box(
                        modifier = Modifier
                            .onGloballyPositioned { coordinates ->
                                tabBounds[tab.projectId] = coordinates.boundsInParent()
                            }
                            .zIndex(if (draggedTabId == tab.projectId) 2f else 0f)
                            .graphicsLayer {
                                if (draggedTabId == tab.projectId) {
                                    translationX = dragTranslation.x
                                    translationY = dragTranslation.y
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .width(TAB_WIDTH_DP.dp)
                                .height(28.dp)
                                .background(bg)
                                .then(
                                    when (tab.projectId) {
                                        draggedTabId -> Modifier.border(2.dp, MegaDriveAccent)
                                        dropTargetTabId -> Modifier.border(2.dp, MegaDrivePrimary)
                                        else -> Modifier
                                    }
                                )
                                .then(
                                    if (reorderMode) {
                                        Modifier.pointerInput(tab.projectId) {
                                            var gestureTargetId: Long? = tab.projectId
                                            detectDragGestures(
                                                onDragStart = { localStart ->
                                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    menuTabId = null
                                                    draggedTabId = tab.projectId
                                                    dropTargetTabId = tab.projectId
                                                    gestureTargetId = tab.projectId
                                                    dragTranslation = Offset.Zero
                                                    dragPointer =
                                                        (tabBounds[tab.projectId]?.topLeft ?: Offset.Zero) + localStart
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    dragPointer += dragAmount
                                                    dragTranslation += dragAmount
                                                    val currentTabs = latestDisplayedTabs
                                                    val targetIndex = tabDropTargetIndex(
                                                        tabIds = currentTabs.map { it.projectId },
                                                        bounds = tabBounds,
                                                        pointer = dragPointer
                                                    )
                                                    gestureTargetId = targetIndex
                                                        ?.let { currentTabs.getOrNull(it)?.projectId }
                                                    dropTargetTabId = gestureTargetId
                                                },
                                                onDragEnd = {
                                                    val currentTabs = latestDisplayedTabs
                                                    val fromIndex = currentTabs.indexOfFirst {
                                                        it.projectId == tab.projectId
                                                    }
                                                    val toIndex = currentTabs.indexOfFirst {
                                                        it.projectId == gestureTargetId
                                                    }
                                                    if (fromIndex >= 0 && toIndex >= 0 && fromIndex != toIndex) {
                                                        draftOrder = moveTabId(
                                                            currentTabs.map { it.projectId },
                                                            fromIndex,
                                                            toIndex
                                                        )
                                                    }
                                                    draggedTabId = null
                                                    dropTargetTabId = null
                                                    dragTranslation = Offset.Zero
                                                },
                                                onDragCancel = {
                                                    draggedTabId = null
                                                    dropTargetTabId = null
                                                    dragTranslation = Offset.Zero
                                                }
                                            )
                                        }
                                    } else {
                                        Modifier.combinedClickable(
                                            onClick = { onSelect(tab.projectId) },
                                            onLongClick = { menuTabId = tab.projectId }
                                        )
                                    }
                                )
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
                                        "Reorder tabs",
                                        color = if (tabs.size > 1) Color.White else MegaDriveDim,
                                        fontFamily = MonoFontFamily,
                                        fontSize = 12.sp
                                    )
                                },
                                enabled = tabs.size > 1,
                                onClick = {
                                    menuTabId = null
                                    draftOrder = tabs.map { it.projectId }
                                    reorderMode = true
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Restart tmux…",
                                        color = if (tab.usesTmux && !tab.isConnecting) {
                                            Color.White
                                        } else {
                                            MegaDriveDim
                                        },
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
                                    Text(
                                        "Close",
                                        color = Color.White,
                                        fontFamily = MonoFontFamily,
                                        fontSize = 12.sp
                                    )
                                },
                                onClick = {
                                    menuTabId = null
                                    onClose(tab.projectId, tab.sessionId)
                                }
                            )
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .height(28.dp)
                    .clickable(enabled = !reorderMode) { onAddProject() }
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("+", color = if (reorderMode) MegaDriveDim else MegaDriveAccent, fontSize = 14.sp)
            }
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

internal fun moveTabId(tabIds: List<Long>, fromIndex: Int, toIndex: Int): List<Long> {
    if (fromIndex !in tabIds.indices || toIndex !in tabIds.indices || fromIndex == toIndex) {
        return tabIds
    }
    return tabIds.toMutableList().apply {
        add(toIndex, removeAt(fromIndex))
    }
}
