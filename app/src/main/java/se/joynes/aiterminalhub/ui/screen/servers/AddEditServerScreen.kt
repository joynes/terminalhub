package se.joynes.aiterminalhub.ui.screen.servers

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
fun AddEditServerScreen(
    serverId: Long?,
    onBack: () -> Unit,
    viewModel: AddEditServerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(serverId) { viewModel.loadServer(serverId) }
    LaunchedEffect(state.saved) { if (state.saved) onBack() }

    Scaffold(
        topBar = {
            RetroTopBar(
                title = if (serverId == null) "ADD SERVER" else "EDIT SERVER",
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
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RetroTextField(state.name, { viewModel.update { copy(name = it) } }, "Name *", Modifier.fillMaxWidth())
            RetroTextField(state.host, { viewModel.update { copy(host = it) } }, "Hostname / IP *", Modifier.fillMaxWidth())
            RetroTextField(state.port, { viewModel.update { copy(port = it) } }, "Port (default 22)", Modifier.fillMaxWidth())
            RetroTextField(state.username, { viewModel.update { copy(username = it) } }, "Username *", Modifier.fillMaxWidth())
            RetroTextField(state.password, { viewModel.update { copy(password = it) } }, "Password", Modifier.fillMaxWidth())
            RetroTextField(state.projectsFolder, { viewModel.update { copy(projectsFolder = it) } }, "Projects Folder", Modifier.fillMaxWidth())

            Spacer(Modifier.height(4.dp))
            Text("SETUP SCRIPT", color = MegaDrivePrimary, fontSize = 12.sp, fontFamily = MonoFontFamily)
            Text("Placeholders: {{PROJECT_NAME}}, {{PROJECT_PATH}}, {{SESSION_NAME}}", color = MegaDriveDim, fontSize = 10.sp, fontFamily = MonoFontFamily)
            OutlinedTextField(
                value = state.setupScript,
                onValueChange = { viewModel.update { copy(setupScript = it) } },
                modifier = Modifier.fillMaxWidth().height(180.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MegaDrivePrimary,
                    unfocusedBorderColor = MegaDriveDim,
                    focusedTextColor = MegaDriveOnSurface,
                    unfocusedTextColor = MegaDriveOnSurface,
                    cursorColor = MegaDrivePrimary
                )
            )

            Spacer(Modifier.height(8.dp))
            RetroButton(
                text = "[ SAVE ]",
                onClick = { viewModel.save() },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.host.isNotBlank() && state.username.isNotBlank()
            )
        }
    }
}
