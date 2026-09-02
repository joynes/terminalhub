package se.joynes.terminalhub.ui.screen.servers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import se.joynes.terminalhub.data.db.entity.ServerEntity
import se.joynes.terminalhub.data.security.HostKeyChallengeKind
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
    var showForgetConfirmation by remember { mutableStateOf(false) }
    var showServerSetupHelp by rememberSaveable { mutableStateOf(false) }
    var showPasswordOption by rememberSaveable { mutableStateOf(false) }
    var showPrivateKeyEntry by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(serverId) { viewModel.loadServer(serverId) }
    LaunchedEffect(state.saved) { if (state.saved) onBack() }

    state.hostKeyChallenge?.let { challenge ->
        val changed = challenge.kind == HostKeyChallengeKind.CHANGED
        AlertDialog(
            onDismissRequest = viewModel::cancelHostKeyChallenge,
            title = { Text(if (changed) "SSH HOST KEY CHANGED" else "TRUST SSH HOST?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (changed) {
                            "Connection blocked. This can indicate a reinstalled server or an attack. Verify the change outside TerminalHub. To continue, cancel, use Forget trusted key, then test SSH again."
                        } else {
                            "First contact with this host and port. Compare this fingerprint with the server before trusting it."
                        }
                    )
                    challenge.trustedFingerprint?.let { Text("Trusted: $it", fontFamily = MonoFontFamily, fontSize = 11.sp) }
                    Text("Presented algorithm: ${challenge.presentedAlgorithm}", fontFamily = MonoFontFamily, fontSize = 11.sp)
                    Text("Presented: ${challenge.presentedFingerprint}", fontFamily = MonoFontFamily, fontSize = 11.sp)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = if (changed) viewModel::cancelHostKeyChallenge else viewModel::trustPresentedHostKey
                ) { Text(if (changed) "OK" else "TRUST AND RETRY") }
            },
            dismissButton = if (changed) null else {
                { TextButton(onClick = viewModel::cancelHostKeyChallenge) { Text("CANCEL") } }
            }
        )
    }

    if (showForgetConfirmation) {
        AlertDialog(
            onDismissRequest = { showForgetConfirmation = false },
            title = { Text("FORGET TRUSTED KEY?") },
            text = { Text("This affects every server profile using this host and port. The next connection will be blocked until you verify and trust its fingerprint again.") },
            confirmButton = {
                TextButton(onClick = {
                    showForgetConfirmation = false
                    viewModel.forgetTrustedHostKey()
                }) { Text("FORGET") }
            },
            dismissButton = {
                TextButton(onClick = { showForgetConfirmation = false }) { Text("CANCEL") }
            }
        )
    }

    if (showServerSetupHelp) {
        AlertDialog(
            onDismissRequest = { showServerSetupHelp = false },
            title = { Text("START OR FIND A SERVER") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("1. Start SSH on the computer you want to reach.")
                    Text("macOS: System Settings → General → Sharing → Remote Login.\nUbuntu/Debian: sudo apt install openssh-server && sudo systemctl enable --now ssh\nWindows: Settings → System → Optional features → OpenSSH Server, then start the OpenSSH SSH Server service.")
                    Text("2. Find its local IP address. macOS/Linux: hostname -I or ip addr. Windows: ipconfig. Use an address such as 192.168.1.42 while your phone is on the same Wi-Fi.")
                    Text("3. Enter that IP, port 22, and the username from the computer. Generate an SSH key, install or copy only its public part, then Test SSH and verify the displayed fingerprint.")
                    Text("For access away from home, use a secure private network such as Tailscale or configure your network carefully. Do not expose SSH to the internet without understanding the security implications.")
                }
            },
            confirmButton = {
                TextButton(onClick = { showServerSetupHelp = false }) { Text("GOT IT") }
            }
        )
    }

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
                body = "Use SSH keys. The private key is encrypted and kept only on this Android device; the server only needs the public key. Password login is an optional one-time setup method."
            )
            RetroButton(
                text = "[ HOW TO START A SERVER / FIND ITS IP ]",
                onClick = { showServerSetupHelp = true },
                modifier = Modifier.fillMaxWidth()
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

            SectionTitle("SSH HOST IDENTITY")
            when {
                state.knownHostStoreCorrupt -> {
                    NeonStatusBadge("TRUST RECORD ERROR", MegaDriveError)
                    HelpText("The local trusted-host record is corrupt. Forget it, then test SSH and verify the fingerprint again.")
                }
                state.knownHost != null -> {
                    NeonStatusBadge("HOST KEY TRUSTED", MegaDriveGreen)
                    state.knownHost?.let { knownHost ->
                        Text(
                            "${knownHost.algorithm}\n${knownHost.fingerprint}",
                            color = MegaDriveOnSurface,
                            fontSize = 10.sp,
                            fontFamily = MonoFontFamily
                        )
                    }
                }
                else -> {
                    NeonStatusBadge("HOST KEY NOT TRUSTED", MegaDriveWarning)
                    HelpText("Test SSH to see and verify the server's SHA-256 host-key fingerprint.")
                }
            }
            if (state.knownHost != null || state.knownHostStoreCorrupt) {
                RetroButton(
                    text = "[ FORGET TRUSTED KEY ]",
                    onClick = { showForgetConfirmation = true },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            SectionTitle("2. AUTHENTICATION")
            Text("SSH KEY — RECOMMENDED", color = MegaDrivePrimary, fontSize = 12.sp, fontFamily = MonoFontFamily)
            HelpText("Generate a key here. TerminalHub stores its private half encrypted on this device; install or copy only the public half to the server.")
            if (showPrivateKeyEntry) {
                OutlinedTextField(
                    value = state.privateKey,
                    onValueChange = { viewModel.update { copy(privateKey = it) } },
                    placeholder = {
                        Text("Paste an existing PEM private key to replace the local key.", color = MegaDriveDim, fontSize = 11.sp, fontFamily = MonoFontFamily)
                    },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = MonoFontFamily, fontSize = 11.sp, color = MegaDriveOnSurface),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MegaDrivePrimary,
                        unfocusedBorderColor = MegaDriveDim,
                        focusedTextColor = MegaDriveOnSurface,
                        unfocusedTextColor = MegaDriveOnSurface,
                        cursorColor = MegaDrivePrimary
                    )
                )
                RetroButton(
                    text = "[ HIDE PRIVATE KEY FIELD ]",
                    onClick = { showPrivateKeyEntry = false },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                RetroButton(
                    text = if (state.hasSavedPrivateKey || state.privateKey.isNotBlank()) "[ REPLACE PRIVATE KEY ]" else "[ I HAVE A PRIVATE KEY ]",
                    onClick = { showPrivateKeyEntry = true },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (state.privateKey.isNotBlank() || state.hasSavedPrivateKey) {
                NeonStatusBadge(
                    text = if (state.privateKey.isNotBlank()) "KEY READY" else "KEY SAVED",
                    color = MegaDriveGreen
                )
            }
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
                "Automatic install uses a one-time password only to add the public key. It is not needed for normal SSH key login.",
                color = MegaDriveDim,
                fontSize = 10.sp,
                fontFamily = MonoFontFamily
            )
            if (showPasswordOption) {
                Text("ONE-TIME PASSWORD — OPTIONAL", color = MegaDriveWarning, fontSize = 12.sp, fontFamily = MonoFontFamily)
                RetroTextField(
                    state.password,
                    { viewModel.update { copy(password = it) } },
                    "Password for public-key install",
                    Modifier.fillMaxWidth(),
                    isPassword = true
                )
                HelpText("Used only to install the generated public key. Prefer removing or leaving this empty after setup.")
                RetroButton(
                    text = "[ HIDE PASSWORD OPTION ]",
                    onClick = { showPasswordOption = false },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                RetroButton(
                    text = "[ USE ONE-TIME PASSWORD TO INSTALL KEY ]",
                    onClick = { showPasswordOption = true },
                    modifier = Modifier.fillMaxWidth()
                )
            }
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
