package se.joynes.terminalhub.ui.screen.servers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import se.joynes.terminalhub.data.db.entity.ServerEntity
import se.joynes.terminalhub.ui.components.*
import se.joynes.terminalhub.ui.theme.*

@Composable
fun AddEditServerScreen(
    serverId: Long?,
    onBack: () -> Unit,
    viewModel: AddEditServerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val clipboardManager = LocalClipboardManager.current

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
            SecurityNote(
                title = "RECOMMENDED SETUP",
                body = "Use SSH keys. TerminalHub keeps the private key on this Android device. The server only needs the public key. Password login is optional and best used once to install the public key."
            )
            SectionTitle("1. SERVER")
            RetroTextField(state.name, { viewModel.update { copy(name = it) } }, "Display name *", Modifier.fillMaxWidth())
            HelpText("Any name you recognize, for example Home PC, Mac mini, or VPS.")
            RetroTextField(state.host, { viewModel.update { copy(host = it) } }, "Host or IP address *", Modifier.fillMaxWidth())
            HelpText("The SSH address. With Tailscale this is usually the device name or Tailscale IP.")
            RetroTextField(state.port, { viewModel.update { copy(port = it) } }, "SSH port", Modifier.fillMaxWidth())
            HelpText("Usually 22. Change only if your SSH server uses another port.")
            RetroTextField(state.username, { viewModel.update { copy(username = it) } }, "SSH username *", Modifier.fillMaxWidth())
            HelpText("The user on the remote computer, not your Android user.")

            SectionTitle("2. AUTHENTICATION")
            RetroTextField(state.password, { viewModel.update { copy(password = it) } }, "One-time password (optional)", Modifier.fillMaxWidth(), isPassword = true)
            HelpText("Not recommended for normal use. Add it only if you want TerminalHub to install the public key for you once.")
            Text("PRIVATE KEY - LOCAL ONLY", color = MegaDrivePrimary, fontSize = 12.sp, fontFamily = MonoFontFamily)
            OutlinedTextField(
                value = state.privateKey,
                onValueChange = { viewModel.update { copy(privateKey = it) } },
                placeholder = {
                    Text(
                        if (state.hasSavedPrivateKey) "Private key saved locally. Paste a new PEM key only to replace it." else "Optional: paste an existing private key into the app. Do not copy this to the server.",
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
            HelpText("Generate a new key here unless you already know which private key you want to use.")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RetroButton(
                    text = "[ GENERATE KEY ]",
                    onClick = { viewModel.generateKey() },
                    modifier = Modifier.weight(1f)
                )
                RetroButton(
                    text = if (state.keyInstallStatus == KeyInstallStatus.Installing) "[ INSTALLING ]" else "[ INSTALL PUBLIC KEY ]",
                    onClick = { viewModel.installGeneratedKey() },
                    modifier = Modifier.weight(1f),
                    enabled = state.host.isNotBlank() &&
                        state.username.isNotBlank() &&
                        state.password.isNotBlank() &&
                        state.publicKey.isNotBlank() &&
                        state.keyInstallStatus != KeyInstallStatus.Installing
                )
            }
            Text(
                "Automatic install needs host, username, one-time password, and a generated public key.",
                color = MegaDriveDim,
                fontSize = 10.sp,
                fontFamily = MonoFontFamily
            )
            if (state.publicKey.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text("PUBLIC KEY - COPY THIS TO SERVER", color = MegaDrivePrimary, fontSize = 12.sp, fontFamily = MonoFontFamily)
                    RetroButton(
                        text = "[ COPY ]",
                        onClick = { clipboardManager.setText(AnnotatedString(state.publicKey)) }
                    )
                }
                OutlinedTextField(
                    value = state.publicKey,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth().height(92.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = MonoFontFamily,
                        fontSize = 10.sp,
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
                Text(
                    "Manual setup: copy only this public key to the remote user's ~/.ssh/authorized_keys. Never copy the private key to the server.",
                    color = MegaDriveDim,
                    fontSize = 10.sp,
                    fontFamily = MonoFontFamily
                )
            }
            if (state.keyInstallMessage.isNotBlank()) {
                Text(
                    state.keyInstallMessage,
                    color = when (state.keyInstallStatus) {
                        KeyInstallStatus.Success -> MegaDriveGreen
                        KeyInstallStatus.Failure -> MegaDriveError
                        KeyInstallStatus.Installing -> MegaDriveWarning
                        KeyInstallStatus.Idle -> MegaDriveDim
                    },
                    fontSize = 10.sp,
                    fontFamily = MonoFontFamily
                )
            }

            SectionTitle("3. PROJECTS")
            RetroTextField(state.projectsFolder, { viewModel.update { copy(projectsFolder = it) } }, "Remote projects folder", Modifier.fillMaxWidth())
            HelpText("TerminalHub opens project tabs inside this folder on the remote computer.")

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

            SectionTitle("ADVANCED")
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

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = MegaDrivePrimary,
        fontSize = 12.sp,
        fontFamily = MonoFontFamily
    )
}

@Composable
private fun HelpText(text: String) {
    Text(
        text,
        color = MegaDriveDim,
        fontSize = 10.sp,
        fontFamily = MonoFontFamily
    )
}

@Composable
private fun SecurityNote(
    title: String,
    body: String
) {
    RetroCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MegaDriveSurface)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, color = MegaDrivePrimary, fontSize = 12.sp, fontFamily = MonoFontFamily)
            Text(body, color = MegaDriveOnSurface, fontSize = 11.sp, fontFamily = MonoFontFamily)
        }
    }
}
