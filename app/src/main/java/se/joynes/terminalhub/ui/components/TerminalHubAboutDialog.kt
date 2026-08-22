package se.joynes.terminalhub.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.joynes.terminalhub.BuildConfig
import se.joynes.terminalhub.R
import se.joynes.terminalhub.ui.theme.MegaDriveBg
import se.joynes.terminalhub.ui.theme.MegaDriveDim
import se.joynes.terminalhub.ui.theme.MegaDrivePrimary
import se.joynes.terminalhub.ui.theme.MegaDriveSurface
import se.joynes.terminalhub.ui.theme.MonoFontFamily

private const val SOURCE_URL = "https://github.com/joynes/terminalhub"

@Composable
fun TerminalHubAboutDialog(
    onDismiss: () -> Unit,
    versionLabel: String = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MegaDriveSurface,
        title = {
            Text(
                "ABOUT",
                color = MegaDrivePrimary,
                fontFamily = MonoFontFamily,
                fontSize = 14.sp
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground_image),
                    contentDescription = "App icon",
                    modifier = Modifier
                        .size(72.dp)
                        .background(MegaDriveBg)
                        .padding(6.dp)
                )
                Text(
                    "TERMINALHUB",
                    color = Color.White,
                    fontFamily = MonoFontFamily,
                    fontSize = 13.sp
                )
                Text(
                    versionLabel,
                    color = MegaDriveDim,
                    fontFamily = MonoFontFamily,
                    fontSize = 12.sp
                )
                Text(
                    "OPEN SOURCE • GPL-3.0",
                    color = MegaDrivePrimary,
                    fontFamily = MonoFontFamily,
                    fontSize = 12.sp
                )
                Text(
                    "TERMINAL COMPONENTS FROM TERMUX",
                    color = MegaDriveDim,
                    fontFamily = MonoFontFamily,
                    fontSize = 10.sp
                )
                Text(
                    "github.com/joynes/terminalhub",
                    color = MegaDrivePrimary,
                    fontFamily = MonoFontFamily,
                    fontSize = 11.sp,
                    modifier = Modifier.clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_URL)))
                    }
                )
            }
        },
        confirmButton = {
            RetroButton(text = "CLOSE", onClick = onDismiss)
        }
    )
}
