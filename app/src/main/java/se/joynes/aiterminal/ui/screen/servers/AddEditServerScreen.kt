package se.joynes.aiterminal.ui.screen.servers

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
import se.joynes.aiterminal.data.db.entity.ServerEntity
import se.joynes.aiterminal.ui.components.*
import se.joynes.aiterminal.ui.theme.*

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
            RetroTextField(state.password, { viewModel.update { copy(password = it) } }, "Password", Modifier.fillMaxWidth(), isPassword = true)
            Text("PRIVATE KEY", color = MegaDrivePrimary, fontSize = 12.sp, fontFamily = MonoFontFamily)
            OutlinedTextField(
                value = state.privateKey,
                onValueChange = { viewModel.update { copy(privateKey = it) } },
                placeholder = {
                    Text(
                        if (state.hasSavedPrivateKey) "Private key saved. Paste a new PEM key to replace it." else "Paste PEM private key (optional)",
                        color = MegaDriveDim,
                        fontSize = 11.sp,
                        fontFamily = MonoFontFamily
                    )
                },
                modifier = Modifier.fillMaxWidth().height(120.dp),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = MonoFontFamily,
                    fontSize = 11.sp,
                    color = MegaDriveOnSurface
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MegaDrivePrimary,
                    unfocusedBorderColor = MegaDriveDim,
                    focusedTextColor = MegaDriveOnSurface,
                    unfocusedTextColor = MegaDriveOnSurface,
                    cursorColor = MegaDrivePrimary
                )
            )
            if (state.privateKey.isNotBlank() || state.hasSavedPrivateKey) {
                NeonStatusBadge(
                    text = if (state.privateKey.isNotBlank()) "KEY READY" else "KEY SAVED",
                    color = MegaDriveGreen
                )
            }
            RetroTextField(state.projectsFolder, { viewModel.update { copy(projectsFolder = it) } }, "Projects Folder", Modifier.fillMaxWidth())

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                RetroButton(
                    text = if (state.sshTestStatus == SshTestStatus.Testing) "[ TESTING SSH ]" else "[ TEST SSH ]",
                    onClick = { viewModel.testSshConnection() },
                    modifier = Modifier.weight(1f),
                    enabled = state.host.isNotBlank() &&
                        state.username.isNotBlank() &&
                        state.sshTestStatus != SshTestStatus.Testing
                )
                when (state.sshTestStatus) {
                    SshTestStatus.Success -> NeonStatusBadge("SSH OK", MegaDriveGreen)
                    SshTestStatus.Failure -> NeonStatusBadge("SSH FAIL", MegaDriveError)
                    SshTestStatus.Testing -> NeonStatusBadge("TESTING", MegaDriveWarning)
                    SshTestStatus.Idle -> NeonStatusBadge("NOT TESTED", MegaDriveDim)
                }
            }
            if (state.sshTestMessage.isNotBlank()) {
                Text(
                    state.sshTestMessage,
                    color = when (state.sshTestStatus) {
                        SshTestStatus.Success -> MegaDriveGreen
                        SshTestStatus.Failure -> MegaDriveError
                        SshTestStatus.Testing -> MegaDriveWarning
                        SshTestStatus.Idle -> MegaDriveDim
                    },
                    fontSize = 10.sp,
                    fontFamily = MonoFontFamily
                )
            }

            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text("SETUP SCRIPT", color = MegaDrivePrimary, fontSize = 12.sp, fontFamily = MonoFontFamily)
                RetroButton(
                    text = "[ RESET ]",
                    onClick = { viewModel.update { copy(setupScript = ServerEntity.DEFAULT_SETUP_SCRIPT) } }
                )
            }
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
