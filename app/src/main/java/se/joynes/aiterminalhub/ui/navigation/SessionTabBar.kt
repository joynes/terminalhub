package se.joynes.aiterminalhub.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.joynes.aiterminalhub.domain.TerminalSessionId
import se.joynes.aiterminalhub.ui.screen.sessions.ProjectTabState
import se.joynes.aiterminalhub.ui.theme.*

private const val TAB_WIDTH_DP = 76

/** Deterministic hue from seed → dark background colour that reads well against light text. */
private fun tabColor(seed: Int, active: Boolean): Color {
    val hue = ((seed.toLong() and 0x7FFFFFFF) % 360).toFloat()
    return if (active)
        Color.hsl(hue, saturation = 0.60f, lightness = 0.30f)
    else
        Color.hsl(hue, saturation = 0.30f, lightness = 0.12f)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SessionTabBar(
    tabs: List<ProjectTabState>,
    activeId: TerminalSessionId?,
    onSelect: (TerminalSessionId) -> Unit,
    onClose: (Long, TerminalSessionId?) -> Unit,
    onMove: (Int, Int) -> Unit,
    onAddProject: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuTabIndex by remember { mutableStateOf<Int?>(null) }

    LazyRow(
        modifier = modifier
            .background(MegaDriveSurface)
            .height(28.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        itemsIndexed(tabs) { index, tab ->
            val isSelected = tab.sessionId != null && tab.sessionId == activeId
            val bg = tabColor(tab.colorSeed, isSelected)
            val textColor = when {
                isSelected      -> Color.White
                tab.isConnecting -> MegaDrivePrimary
                tab.isConnected -> Color.White.copy(alpha = 0.65f)
                else            -> Color.White.copy(alpha = 0.28f)
            }
            Box {
                Row(
                    modifier = Modifier
                        .width(TAB_WIDTH_DP.dp)
                        .fillMaxHeight()
                        .background(bg)
                        .combinedClickable(
                            onClick = { tab.sessionId?.let { onSelect(it) } },
                            onLongClick = { menuTabIndex = index }
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
                    expanded = menuTabIndex == index,
                    onDismissRequest = { menuTabIndex = null }
                ) {
                    DropdownMenuItem(
                        text = {
                            Text("Close", color = Color.White, fontFamily = MonoFontFamily, fontSize = 12.sp)
                        },
                        onClick = {
                            menuTabIndex = null
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
                            menuTabIndex = null
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
                            menuTabIndex = null
                            if (index < tabs.lastIndex) onMove(index, index + 1)
                        }
                    )
                }
            }
        }
        item {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .clickable { onAddProject() }
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("+", color = MegaDriveAccent, fontSize = 14.sp)
            }
        }
    }
}
