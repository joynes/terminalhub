package se.joynes.terminalhub.ui.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import se.joynes.terminalhub.data.settings.TerminalScrollSettings
import java.util.Locale

@Composable
internal fun TerminalScrollSettingsEditor(settings: TerminalScrollSettings, onChange: (TerminalScrollSettings) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("TOUCH SCROLLING", style = MaterialTheme.typography.titleSmall)
        Text("Applies immediately. tmux and full-screen apps interpret wheel events themselves, so their speed can differ from normal terminal history.", style = MaterialTheme.typography.bodySmall)
        Row {
            TextButton(onClick = { onChange(TerminalScrollSettings.gentle) }) { Text("Gentle") }
            TextButton(onClick = { onChange(TerminalScrollSettings()) }) { Text("Default") }
            TextButton(onClick = { onChange(TerminalScrollSettings.fast) }) { Text("Fast") }
        }
        ScrollSlider("Terminal speed", "Finger movement through ordinary terminal history", settings.terminalSpeed, 0.25f..5f) { onChange(settings.copy(terminalSpeed = it)) }
        ScrollSlider("tmux / full-screen speed", "Finger movement sent as wheel events or arrow keys", settings.remoteSpeed, 0.25f..5f) { onChange(settings.copy(remoteSpeed = it)) }
        Row {
            Checkbox(settings.inertia, { onChange(settings.copy(inertia = it)) })
            Text("Keep scrolling after a flick")
        }
        ScrollSlider("Flick speed", "Higher values give a faster, longer glide", settings.flingSpeed, 0.25f..4f, settings.inertia) { onChange(settings.copy(flingSpeed = it)) }
        ScrollSlider("Braking", "Higher values stop sooner; lower values glide longer", settings.braking, 0.25f..4f, settings.inertia) { onChange(settings.copy(braking = it)) }
        ScrollSlider("Minimum flick speed", "Ignore slow releases below this speed (pixels/second)", settings.minimumFlickSpeed, 50f..2000f, settings.inertia, false) { onChange(settings.copy(minimumFlickSpeed = it)) }
        ScrollSlider("Remote flick limit", "Maximum wheel/arrow events per flick; keeps SSH traffic bounded", settings.remoteFlingLimit.toFloat(), 10f..1000f, settings.inertia, false) { onChange(settings.copy(remoteFlingLimit = it.toInt())) }
        ScrollSlider("Remote events per frame", "Lower values send smaller bursts during after-scroll", settings.remoteEventsPerFrame.toFloat(), 1f..20f, settings.inertia, false) { onChange(settings.copy(remoteEventsPerFrame = it.toInt())) }
        Row {
            Checkbox(settings.reverseDirection, { onChange(settings.copy(reverseDirection = it)) })
            Text("Reverse finger-scroll direction")
        }
        Text("Touch the terminal to stop a glide. Switching tabs also stops it. Terminal text moves in rows, not individual pixels.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ScrollSlider(title: String, explanation: String, value: Float, range: ClosedFloatingPointRange<Float>,
    enabled: Boolean = true, multiplier: Boolean = true, onChange: (Float) -> Unit) {
    Text("$title: ${if (multiplier) String.format(Locale.ROOT, "%.2f×", value) else value.toInt().toString()}")
    Text(explanation, style = MaterialTheme.typography.bodySmall)
    Slider(value, onChange, valueRange = range, enabled = enabled,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = title })
}
