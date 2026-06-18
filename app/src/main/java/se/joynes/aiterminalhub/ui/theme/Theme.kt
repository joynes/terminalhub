package se.joynes.aiterminalhub.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val RetroColorScheme = darkColorScheme(
    primary = MegaDrivePrimary,
    onPrimary = MegaDriveBg,
    secondary = MegaDriveAccent,
    onSecondary = MegaDriveBg,
    tertiary = MegaDriveWarning,
    background = MegaDriveBg,
    surface = MegaDriveSurface,
    onBackground = MegaDriveOnSurface,
    onSurface = MegaDriveOnSurface,
    error = MegaDriveError,
    onError = MegaDriveBg
)

@Composable
fun AITerminalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RetroColorScheme,
        typography = RetroTypography,
        content = content
    )
}
