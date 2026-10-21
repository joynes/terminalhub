package se.joynes.terminalhub.ui.screen.terminal

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun SshTunnelDialog(serverId: Long?, onDismiss: () -> Unit, viewModel: SshTunnelViewModel = hiltViewModel()) {
    val tunnels by viewModel.tunnels.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val error by viewModel.error.collectAsState()
    var localPort by rememberSaveable { mutableStateOf("18080") }
    var remotePort by rememberSaveable { mutableStateOf("3000") }
    var browserError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    AlertDialog(onDismissRequest = onDismiss, title = { Text("SSH web tunnels") }, text = {
        Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Open a web service running on your SSH server in this phone's browser. The phone listens only on 127.0.0.1, not Wi-Fi.")
            Text("The server must allow TCP forwarding. Tunnels last while the app process and SSH connection stay alive. Stop and start again after a lost connection.")
            if (serverId == null) Text("Select a remote server project before starting a tunnel.")
            OutlinedTextField(remotePort, { remotePort = it }, singleLine = true, label = { Text("Server port (e.g. 3000)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(localPort, { localPort = it }, singleLine = true, label = { Text("Phone port (1024–65535)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            TextButton(enabled = serverId != null && !busy, onClick = { viewModel.start(serverId!!, localPort, remotePort) }) { Text("Start tunnel on selected server") }
            if (busy) CircularProgressIndicator(Modifier.size(24.dp))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            browserError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            tunnels.forEach { tunnel ->
                HorizontalDivider()
                Text("${tunnel.serverName} · server port ${tunnel.remotePort}")
                Text(tunnel.url)
                Row {
                    TextButton(onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(tunnel.url))) }
                            .onFailure { browserError = "No browser could open this tunnel" }
                    }) { Text("Open browser") }
                    TextButton(enabled = !busy, onClick = { viewModel.stop(tunnel.localPort) }) { Text("Stop") }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}
