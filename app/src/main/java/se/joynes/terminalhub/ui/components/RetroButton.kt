package se.joynes.terminalhub.ui.components

import androidx.compose.foundation.border
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import se.joynes.terminalhub.ui.theme.*

@Composable
fun RetroButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.border(1.dp, if (enabled) MegaDrivePrimary else MegaDriveDim),
        colors = ButtonDefaults.buttonColors(
            containerColor = MegaDriveBg,
            contentColor = MegaDrivePrimary,
            disabledContainerColor = MegaDriveSurface,
            disabledContentColor = MegaDriveDim
        )
    ) {
        Text(text = text, fontFamily = MonoFontFamily)
    }
}
