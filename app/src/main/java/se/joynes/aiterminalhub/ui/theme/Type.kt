package se.joynes.aiterminalhub.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Courier-style monospace as fallback (PressStart2P would be added as a font asset)
val MonoFontFamily = FontFamily.Monospace

val RetroTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = MonoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        color = MegaDrivePrimary
    ),
    headlineMedium = TextStyle(
        fontFamily = MonoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        color = MegaDrivePrimary
    ),
    titleLarge = TextStyle(
        fontFamily = MonoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        color = MegaDrivePrimary
    ),
    titleMedium = TextStyle(
        fontFamily = MonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        color = MegaDriveOnSurface
    ),
    bodyLarge = TextStyle(
        fontFamily = MonoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = MegaDriveOnSurface
    ),
    bodyMedium = TextStyle(
        fontFamily = MonoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        color = MegaDriveOnSurface
    ),
    labelSmall = TextStyle(
        fontFamily = MonoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        color = MegaDriveDim
    )
)
