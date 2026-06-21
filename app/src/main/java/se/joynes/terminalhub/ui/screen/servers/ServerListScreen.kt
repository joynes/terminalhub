package se.joynes.terminalhub.ui.screen.servers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import se.joynes.terminalhub.data.model.Server
import se.joynes.terminalhub.ui.components.*
import se.joynes.terminalhub.ui.theme.*

@Composable
fun ServerListScreen(
    onAddServer: () -> Unit,
    onEditServer: (Long) -> Unit,
    onOpenLog: () -> Unit,
    onOpenSessionLog: () -> Unit,
    viewModel: ServerListViewModel = hiltViewModel()
) {
    val servers by viewModel.servers.collectAsState()
    var pendingDelete by remember { mutableStateOf<Server?>(null) }

    pendingDelete?.let { server ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = MegaDriveSurface,
            title = {
                Text(
                    "DELETE SERVER?",
                    color = MegaDrivePrimary,
                    fontFamily = MonoFontFamily,
                    fontSize = 14.sp
                )
            },
            text = {
                Text(
                    "Delete ${server.name}? Stored credentials and projects for this server will also be removed.",
                    color = MegaDriveOnSurface,
                    fontFamily = MonoFontFamily,
                    fontSize = 12.sp
                )
            },
            dismissButton = {
                RetroButton("CANCEL", onClick = { pendingDelete = null })
            },
            confirmButton = {
                RetroButton(
                    "DELETE",
                    onClick = {
                        viewModel.deleteServer(server)
                        pendingDelete = null
                    }
                )
            }
        )
    }

    Scaffold(
        topBar = {
            RetroTopBar(
                title = "SERVERS",
                actions = {
                    IconButton(onClick = onOpenSessionLog) {
                        Text("SLOG", color = MegaDriveDim, fontSize = 10.sp, fontFamily = MonoFontFamily)
                    }
                    IconButton(onClick = onOpenLog) {
                        Text("LOG", color = MegaDriveWarning, fontSize = 10.sp, fontFamily = MonoFontFamily)
                    }
                    IconButton(onClick = onAddServer) {
                        Icon(Icons.Default.Add, "Add server", tint = MegaDrivePrimary)
                    }
                }
            )
        },
        containerColor = MegaDriveBg
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).background(MegaDriveBg)) {
            if (servers.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("NO SERVERS CONFIGURED", color = MegaDriveDim, fontSize = 12.sp, fontFamily = MonoFontFamily)
                    Spacer(Modifier.height(16.dp))
                    RetroButton("[ ADD SERVER ]", onAddServer)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(servers) { server ->
                        ServerCard(
                            server = server,
                            onEdit = { onEditServer(server.id) },
                            onDelete = { pendingDelete = server }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerCard(
    server: Server,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    RetroCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(server.name, color = MegaDrivePrimary, fontFamily = MonoFontFamily, fontSize = 14.sp)
                NeonStatusBadge(text = server.authType.uppercase(), color = MegaDriveAccent)
            }
            Spacer(Modifier.height(4.dp))
            Text("${server.username}@${server.host}:${server.port}", color = MegaDriveOnSurface, fontSize = 12.sp, fontFamily = MonoFontFamily)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RetroButton("EDIT", onEdit, Modifier.weight(1f))
                RetroButton("DELETE", onDelete, Modifier.weight(1f))
            }
        }
    }
}
