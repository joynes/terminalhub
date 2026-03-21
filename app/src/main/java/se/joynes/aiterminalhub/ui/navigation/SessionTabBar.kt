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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.joynes.aiterminalhub.domain.TerminalSessionId
import se.joynes.aiterminalhub.ui.screen.sessions.ProjectTabState
import se.joynes.aiterminalhub.ui.theme.*

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
            .fillMaxWidth()
            .background(MegaDriveSurface)
            .height(40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(tabs) { tab ->
            val isSelected = tab.sessionId != null && tab.sessionId == activeId
            val labelColor = when {
                isSelected -> MegaDrivePrimary
                tab.isConnected -> MegaDriveOnSurface
                else -> MegaDriveDim   // connecting = dimmed
            }
            Row(
                modifier = Modifier
                    .clickable { tab.sessionId?.let { onSelect(it) } }
                    .background(if (isSelected) MegaDrivePrimary.copy(alpha = 0.2f) else MegaDriveBg)
                    .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = tab.projectName.uppercase(),
                    color = labelColor,
                    fontSize = 11.sp,
                    fontFamily = MonoFontFamily
                )
                Text(
                    text = "×",
                    color = MegaDriveAccent,
                    fontSize = 14.sp,
                    fontFamily = MonoFontFamily,
                    modifier = Modifier.clickable { onClose(tab.projectId, tab.sessionId) }
                )
            }
        }
        item {
            Box(
                modifier = Modifier
                    .clickable { onAddProject() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("+", color = MegaDriveAccent, fontSize = 18.sp)
            }
        }
    }
}
