package se.joynes.terminalhub.ui.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import se.joynes.terminalhub.data.settings.AppSettings
import kotlin.math.roundToInt

@Composable
internal fun TabAppearanceEditor(settings: AppSettings, onChange: (Boolean, Int, Int, Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Project tabs", style = MaterialTheme.typography.titleSmall)
        TabSizeSliders("Project", settings.projectTabWidth, settings.projectTabHeight, settings.projectTabTextSize, 200) { w, h, t -> onChange(true, w, h, t) }
        TextButton(onClick = { onChange(true, 70, 28, 9) }) { Text("Reset project tabs") }
        Text("Terminal tabs", style = MaterialTheme.typography.titleSmall)
        Text("Hidden when a project has one terminal. Hold a project tab → New terminal to add another.", style = MaterialTheme.typography.bodySmall)
        TabSizeSliders("Terminal", settings.terminalTabWidth, settings.terminalTabHeight, settings.terminalTabTextSize, 240) { w, h, t -> onChange(false, w, h, t) }
        TextButton(onClick = { onChange(false, 100, 28, 12) }) { Text("Reset terminal tabs") }
        Text("Narrow tabs fit more projects but shorten names. Larger text may increase the height so labels stay readable. These controls do not change terminal content text.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TabSizeSliders(prefix: String, width: Int, height: Int, text: Int, maxWidth: Int, onChange: (Int, Int, Int) -> Unit) {
    TabSlider("$prefix tab width", width, 48..maxWidth, "dp") { onChange(it, height, text) }
    TabSlider("$prefix tab height", height, 24..64, "dp") { onChange(width, it, text) }
    TabSlider("$prefix tab text size", text, 8..24, "sp") { onChange(width, height, it) }
}

@Composable
private fun TabSlider(label: String, value: Int, range: IntRange, unit: String, onChange: (Int) -> Unit) {
    Text("$label: $value $unit")
    Slider(value.toFloat(), { onChange(it.roundToInt()) }, valueRange = range.first.toFloat()..range.last.toFloat(),
        steps = range.last - range.first - 1, modifier = Modifier.fillMaxWidth().semantics { contentDescription = label })
}
