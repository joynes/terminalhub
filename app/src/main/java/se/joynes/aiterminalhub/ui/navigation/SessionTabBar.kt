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
import se.joynes.aiterminalhub.domain.TerminalSessionMeta
import se.joynes.aiterminalhub.ui.theme.*

@Composable
fun SessionTabBar(
    sessions: List<TerminalSessionMeta>,
    activeId: TerminalSessionId?,
    onSelect: (TerminalSessionId) -> Unit,
    onClose: (TerminalSessionId) -> Unit,
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
        items(sessions) { session ->
            val isSelected = session.id == activeId
            Row(
                modifier = Modifier
                    .clickable { onSelect(session.id) }
                    .background(if (isSelected) MegaDrivePrimary.copy(alpha = 0.2f) else MegaDriveBg)
                    .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = session.projectName.uppercase(),
                    color = if (isSelected) MegaDrivePrimary
                            else if (session.isConnected) MegaDriveOnSurface
                            else MegaDriveDim,
                    fontSize = 11.sp,
                    fontFamily = MonoFontFamily
                )
                Text(
                    text = "×",
                    color = MegaDriveAccent,
                    fontSize = 14.sp,
                    fontFamily = MonoFontFamily,
                    modifier = Modifier.clickable { onClose(session.id) }
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
