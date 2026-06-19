package se.joynes.terminalhub.ui.components

import androidx.compose.foundation.border
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import se.joynes.terminalhub.ui.theme.*

@Composable
fun RetroTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    isPassword: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = MegaDriveDim, fontFamily = MonoFontFamily) },
        singleLine = singleLine,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = modifier,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MegaDrivePrimary,
            unfocusedBorderColor = MegaDriveDim,
            focusedTextColor = MegaDriveOnSurface,
            unfocusedTextColor = MegaDriveOnSurface,
            cursorColor = MegaDrivePrimary,
            focusedLabelColor = MegaDrivePrimary
        )
    )
}
