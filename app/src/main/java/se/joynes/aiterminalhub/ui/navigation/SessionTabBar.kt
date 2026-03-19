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
import se.joynes.aiterminalhub.ui.theme.*

@Composable
fun SessionTabBar(
    sessionIds: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .background(MegaDriveSurface)
            .height(40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        itemsIndexed(sessionIds) { index, id ->
            val isSelected = index == selectedIndex
            Box(
                modifier = Modifier
                    .clickable { onTabSelected(index) }
                    .background(if (isSelected) MegaDrivePrimary.copy(alpha = 0.2f) else MegaDriveBg)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "SSH ${index + 1}",
                    color = if (isSelected) MegaDrivePrimary else MegaDriveDim,
                    fontSize = 12.sp,
                    fontFamily = MonoFontFamily
                )
            }
        }
        item {
            Box(
                modifier = Modifier
                    .clickable { /* TODO: add new session */ }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text("+", color = MegaDriveAccent, fontSize = 18.sp)
            }
        }
    }
}
