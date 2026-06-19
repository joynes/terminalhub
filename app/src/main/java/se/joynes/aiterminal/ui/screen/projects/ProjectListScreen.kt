package se.joynes.aiterminal.ui.screen.projects

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
import se.joynes.aiterminal.data.model.Project
import se.joynes.aiterminal.ui.components.*
import se.joynes.aiterminal.ui.theme.*

@Composable
fun ProjectListScreen(
    serverId: Long,
    onAddProject: () -> Unit,
    onEditProject: (Long) -> Unit,
    onConnect: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: ProjectListViewModel = hiltViewModel()
) {
    val projects by viewModel.projects.collectAsState()
    LaunchedEffect(serverId) { viewModel.loadProjects(serverId) }

    Scaffold(
        topBar = {
            RetroTopBar(
                title = "PROJECTS",
                onBack = onBack,
                actions = {
                    IconButton(onClick = onAddProject) {
                        Icon(Icons.Default.Add, "Add project", tint = MegaDrivePrimary)
                    }
                }
            )
        },
        containerColor = MegaDriveBg
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).background(MegaDriveBg)) {
            if (projects.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("NO PROJECTS", color = MegaDriveDim, fontSize = 12.sp, fontFamily = MonoFontFamily)
                    Spacer(Modifier.height(16.dp))
                    RetroButton("[ ADD PROJECT ]", onAddProject)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(projects) { project ->
                        RetroCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(project.name, color = MegaDrivePrimary, fontFamily = MonoFontFamily, fontSize = 14.sp)
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    RetroButton("CONNECT", { onConnect(project.id) }, Modifier.weight(1f))
                                    RetroButton("EDIT", { onEditProject(project.id) }, Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
