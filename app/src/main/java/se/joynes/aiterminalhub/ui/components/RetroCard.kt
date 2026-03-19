package se.joynes.aiterminalhub.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import se.joynes.aiterminalhub.ui.theme.*

@Composable
fun RetroCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.border(1.dp, MegaDrivePrimary.copy(alpha = 0.5f)),
        colors = CardDefaults.cardColors(containerColor = MegaDriveSurface)
    ) {
        content()
    }
}
