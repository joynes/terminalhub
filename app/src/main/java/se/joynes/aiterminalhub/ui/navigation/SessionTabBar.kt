package se.joynes.aiterminalhub.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

@Composable
fun SessionTabBar(
    tabs: List<ProjectTabState>,
    activeId: TerminalSessionId?,
    onSelect: (TerminalSessionId) -> Unit,
    onClose: (Long, TerminalSessionId?) -> Unit,
    onAddProject: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier
            .background(MegaDriveSurface)
            .height(28.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(tabs) { tab ->
            val isSelected = tab.sessionId != null && tab.sessionId == activeId
            val bg = tabColor(tab.colorSeed, isSelected)
            val textColor = when {
                isSelected      -> Color.White
                tab.isConnected -> Color.White.copy(alpha = 0.65f)
                else            -> Color.White.copy(alpha = 0.28f)
            }
            Row(
                modifier = Modifier
                    .width(TAB_WIDTH_DP.dp)
                    .fillMaxHeight()
                    .background(bg)
                    .clickable { tab.sessionId?.let { onSelect(it) } }
                    .padding(start = 7.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = tab.projectName.uppercase(),
                    color = textColor,
                    fontSize = 9.sp,
                    fontFamily = MonoFontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "×",
                    color = Color.White.copy(alpha = 0.40f),
                    fontSize = 11.sp,
                    fontFamily = MonoFontFamily,
                    modifier = Modifier.clickable { onClose(tab.projectId, tab.sessionId) }
                )
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
