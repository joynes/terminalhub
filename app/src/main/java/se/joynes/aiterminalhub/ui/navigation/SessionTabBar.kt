package se.joynes.aiterminalhub.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.joynes.aiterminalhub.ui.screen.sessions.ProjectTab
import se.joynes.aiterminalhub.ui.theme.*

@Composable
fun SessionTabBar(
    tabs: List<ProjectTab>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    onTabClose: (Int) -> Unit,
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
        itemsIndexed(tabs) { index, tab ->
            val isSelected = index == selectedIndex
            val isConnected = tab.sessionId != null
            Row(
                modifier = Modifier
                    .clickable { onTabSelected(index) }
                    .background(if (isSelected) MegaDrivePrimary.copy(alpha = 0.2f) else MegaDriveBg)
                    .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = tab.project.name.uppercase(),
                    color = if (isSelected) MegaDrivePrimary else if (isConnected) MegaDriveOnSurface else MegaDriveDim,
                    fontSize = 11.sp,
                    fontFamily = MonoFontFamily
                )
                // × close button
                Text(
                    text = "×",
                    color = MegaDriveAccent,
                    fontSize = 14.sp,
                    fontFamily = MonoFontFamily,
                    modifier = Modifier.clickable { onTabClose(index) }
                )
            }
        }
        // + add project button
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
