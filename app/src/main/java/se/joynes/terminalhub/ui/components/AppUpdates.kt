package se.joynes.terminalhub.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import se.joynes.terminalhub.BuildConfig
import se.joynes.terminalhub.ui.theme.MegaDriveDim

/** Use the production listing even from a diagnostic build. Play selects the user's eligible track. */
fun openAppUpdates(context: Context) {
    val intents = listOf(
        Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=se.joynes.terminalhub")).setPackage("com.android.vending"),
        Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=se.joynes.terminalhub"))
    )
    for (intent in intents) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
            // Devices without Google Play can open the web listing instead.
        } catch (_: SecurityException) {
            // A restricted Play app may still allow the browser fallback.
        }
    }
    Toast.makeText(context, "Could not open Google Play. Install or enable a browser and try again.", Toast.LENGTH_LONG).show()
}

@Composable
internal fun AppUpdatesCard() {
    val context = LocalContext.current
    RetroCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("TerminalHub ${BuildConfig.VERSION_NAME}")
            Text("Open Google Play to check for updates and install the version available to your account.", color = MegaDriveDim)
            RetroButton(text = "CHECK FOR UPDATES", onClick = { openAppUpdates(context) })
        }
    }
}
