package se.joynes.aiterminalhub.ui.screen.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RetroTextField(state.name, { viewModel.update { copy(name = it) } }, "Project Name *", Modifier.fillMaxWidth())
            RetroTextField(state.projectPath, { viewModel.update { copy(projectPath = it) } }, "Project Path *", Modifier.fillMaxWidth())
            RetroTextField(state.sessionName, { viewModel.update { copy(sessionName = it) } }, "tmux Session Name *", Modifier.fillMaxWidth())

            Text("SETUP SCRIPT", color = MegaDrivePrimary, fontSize = 12.sp, fontFamily = MonoFontFamily)
            Text("Placeholders: {{PROJECT_NAME}}, {{PROJECT_PATH}}, {{SESSION_NAME}}", color = MegaDriveDim, fontSize = 10.sp, fontFamily = MonoFontFamily)
            OutlinedTextField(
                value = state.setupScript,
                onValueChange = { viewModel.update { copy(setupScript = it) } },
                modifier = Modifier.fillMaxWidth().height(200.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MegaDrivePrimary,
                    unfocusedBorderColor = MegaDriveDim,
                    focusedTextColor = MegaDriveOnSurface,
                    unfocusedTextColor = MegaDriveOnSurface,
                    cursorColor = MegaDrivePrimary
                )
            )

            RetroButton(
                text = "[ SAVE ]",
                onClick = { viewModel.save() },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.name.isNotBlank() && state.projectPath.isNotBlank()
            )
        }
    }
}
