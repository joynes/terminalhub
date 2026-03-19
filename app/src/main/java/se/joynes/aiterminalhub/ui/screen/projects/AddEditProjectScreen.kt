package se.joynes.aiterminalhub.ui.screen.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import se.joynes.aiterminalhub.ui.components.*
import se.joynes.aiterminalhub.ui.theme.*

@Composable
fun AddEditProjectScreen(
    serverId: Long,
    projectId: Long?,
    onBack: () -> Unit,
    viewModel: AddEditProjectViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(projectId) { viewModel.loadProject(serverId, projectId) }
    LaunchedEffect(state.saved) { if (state.saved) onBack() }

    Scaffold(
        topBar = {
            RetroTopBar(
                title = if (projectId == null) "ADD PROJECT" else "EDIT PROJECT",
                onBack = onBack
            )
        },
        containerColor = MegaDriveBg
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MegaDriveBg)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RetroTextField(
                state.name,
                { viewModel.update { copy(name = it) } },
                "Project Name *",
                Modifier.fillMaxWidth()
            )
            Text(
                "Path and tmux session are derived from the server's projects folder and this name.",
                color = MegaDriveDim,
                fontSize = 10.sp,
                fontFamily = MonoFontFamily
            )
            Spacer(Modifier.height(8.dp))
            RetroButton(
                text = "[ SAVE ]",
                onClick = { viewModel.save() },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.name.isNotBlank()
            )
        }
    }
}
