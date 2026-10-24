package se.joynes.terminalhub.ui.screen.sessions

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import se.joynes.terminalhub.domain.MacroRunState

@Composable
internal fun PinnedMacroStatus(state: MacroRunState, onStop: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.testTag("macro-run-status"), tonalElevation = 6.dp, shadowElevation = 4.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.running) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Column(Modifier.weight(1f)) {
                Text("${state.name} · ${state.projectName}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                Text(if (state.running) "Step ${state.step}/${state.total}" else state.message.orEmpty(), style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = if (state.running) onStop else onDismiss) { Text(if (state.running) "STOP" else "CLOSE") }
        }
    }
}
