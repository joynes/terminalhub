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
            Spacer(Modifier.height(4.dp))
            // Setup script override
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    "CUSTOM SETUP SCRIPT",
                    color = MegaDrivePrimary,
                    fontSize = 11.sp,
                    fontFamily = MonoFontFamily
                )
                Switch(
                    checked = state.setupScript != null,
                    onCheckedChange = { on ->
                        viewModel.update { copy(setupScript = if (on) "" else null) }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MegaDrivePrimary,
                        checkedTrackColor = MegaDrivePrimary.copy(alpha = 0.4f)
                    )
                )
            }
            if (state.setupScript != null) {
                Text(
                    "Leave empty to run nothing on connect. Use {{SESSION_NAME}}, {{PROJECT_PATH}}.",
                    color = MegaDriveDim,
                    fontSize = 10.sp,
                    fontFamily = MonoFontFamily
                )
                RetroTextField(
                    value = state.setupScript ?: "",
                    onValueChange = { viewModel.update { copy(setupScript = it) } },
                    label = "Setup script (blank = no tmux)",
                    modifier = Modifier.fillMaxWidth().height(140.dp)
                )
            }
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
