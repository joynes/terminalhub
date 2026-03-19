package se.joynes.aiterminalhub.ui.screen.servers

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
import se.joynes.aiterminalhub.data.model.Server
import se.joynes.aiterminalhub.ui.components.*
import se.joynes.aiterminalhub.ui.theme.*

@Composable
fun ServerListScreen(
    onAddServer: () -> Unit,
    onEditServer: (Long) -> Unit,
    onOpenProjects: (Long) -> Unit,
    onOpenStatus: (Long) -> Unit,
    onOpenUpload: (Long) -> Unit,
    onOpenLog: () -> Unit,
    onOpenSessionLog: () -> Unit,
    viewModel: ServerListViewModel = hiltViewModel()
) {
    val servers by viewModel.servers.collectAsState()

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
                            onOpenProjects = { onOpenProjects(server.id) },
                            onOpenStatus = { onOpenStatus(server.id) },
                            onOpenUpload = { onOpenUpload(server.id) }
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
    onOpenProjects: () -> Unit,
    onOpenStatus: () -> Unit,
    onOpenUpload: () -> Unit
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
                RetroButton("PROJECTS", onOpenProjects, Modifier.weight(1f))
                RetroButton("EDIT", onEdit, Modifier.weight(1f))
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RetroButton("STATUS", onOpenStatus, Modifier.weight(1f))
                RetroButton("UPLOAD", onOpenUpload, Modifier.weight(1f))
            }
        }
    }
}
