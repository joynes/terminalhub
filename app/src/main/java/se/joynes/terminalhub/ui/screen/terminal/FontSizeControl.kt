package se.joynes.terminalhub.ui.screen.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.joynes.terminalhub.ui.components.RetroButton
import se.joynes.terminalhub.ui.theme.*

@Composable
fun FontSizeControl(onIncrease: () -> Unit, onDecrease: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MegaDriveSurface)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("FONT:", color = MegaDriveDim, fontSize = 10.sp, fontFamily = MonoFontFamily)
        RetroButton("A-", onDecrease)
        RetroButton("A+", onIncrease)
    }
}
