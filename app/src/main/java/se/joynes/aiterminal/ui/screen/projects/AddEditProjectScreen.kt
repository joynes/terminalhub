package se.joynes.aiterminal.ui.screen.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import se.joynes.aiterminal.ui.components.*
import se.joynes.aiterminal.ui.theme.*
import se.joynes.aiterminal.data.model.ProjectTargetType

private val AI_TOOLS = listOf(
    "None"         to "",
    "Claude Code"  to "claude",
    "Gemini CLI"   to "gemini",
    "Openclaw"     to "openclaw tui"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditProjectScreen(
    serverId: Long?,
    projectId: Long?,
    onBack: () -> Unit,
    viewModel: AddEditProjectViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(projectId) { viewModel.loadProject(serverId, projectId) }
    LaunchedEffect(state.saved) { if (state.saved) onBack() }
    var serverMenuExpanded by remember { mutableStateOf(false) }
    val normalizedGitUrl = remember(state.gitUrl, state.targetType) {
        AddEditProjectViewModel.normalizeGitUrl(state.gitUrl, state.targetType)
    }
    val selectedServerName = when (state.targetType) {
        ProjectTargetType.LOCAL -> "Local device"
        ProjectTargetType.SSH -> state.serverOptions.firstOrNull { it.id == state.selectedServerId }?.name ?: "Choose server"
    }

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
            Text("TARGET", color = MegaDrivePrimary, fontSize = 12.sp, fontFamily = MonoFontFamily)
            ExposedDropdownMenuBox(
                expanded = serverMenuExpanded,
                onExpandedChange = { serverMenuExpanded = !serverMenuExpanded }
            ) {
                OutlinedTextField(
                    value = selectedServerName,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = MonoFontFamily, fontSize = 12.sp, color = MegaDriveOnSurface
                    ),
                    label = {
                        Text("Server", fontFamily = MonoFontFamily)
                    },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = serverMenuExpanded) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MegaDrivePrimary,
                        unfocusedBorderColor = MegaDriveDim,
                        focusedTextColor = MegaDriveOnSurface,
                        unfocusedTextColor = MegaDriveOnSurface,
                        cursorColor = MegaDrivePrimary
                    )
                )
                ExposedDropdownMenu(
                    expanded = serverMenuExpanded,
                    onDismissRequest = { serverMenuExpanded = false }
                ) {
                    state.serverOptions.forEach { server ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    server.name,
                                    color = MegaDriveOnSurface,
                                    fontFamily = MonoFontFamily,
                                    fontSize = 12.sp
                                )
                            },
                            onClick = {
                                viewModel.update {
                                    copy(
                                        targetType = ProjectTargetType.SSH,
                                        selectedServerId = server.id
                                    )
                                }
                                serverMenuExpanded = false
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Local device",
                                color = MegaDriveOnSurface,
                                fontFamily = MonoFontFamily,
                                fontSize = 12.sp
                            )
                        },
                        onClick = {
                            viewModel.update {
                                copy(
                                    targetType = ProjectTargetType.LOCAL,
                                    selectedServerId = null,
                                    useTmux = false
                                )
                            }
                            serverMenuExpanded = false
                        }
                    )
                }
            }
            Text(
                "Each project still uses one target at a time. Local projects run in a shell on the device. SSH projects run on the selected server.",
                color = MegaDriveDim, fontSize = 10.sp, fontFamily = MonoFontFamily
            )

            // ── Project name ──────────────────────────────────────────────
            RetroTextField(
                state.name,
                { viewModel.update { copy(name = it.replace(" ", "-")) } },
                "Project Name *",
                Modifier.fillMaxWidth()
            )
            Text(
                "No spaces allowed (use dashes). Path and tmux session are derived from this name.",
                color = MegaDriveDim, fontSize = 10.sp, fontFamily = MonoFontFamily
            )

            // ── GIT REPO URL ──────────────────────────────────────────────
            RetroTextField(
                state.gitUrl,
                { viewModel.update { copy(gitUrl = it) } },
                "Git Repo URL (optional)",
                Modifier.fillMaxWidth()
            )
            Text(
                when {
                    state.targetType == ProjectTargetType.LOCAL ->
                        "Local projects do not clone via server SSH. This URL is only used for SSH targets."
                    normalizedGitUrl != state.gitUrl.trim() ->
                        "GitHub HTTPS URLs are converted to SSH on save for server-side clone: $normalizedGitUrl"
                    else ->
                        "If set, the repo will be cloned into the project folder on first connect. For GitHub on servers, prefer SSH form like git@github.com:owner/repo.git"
                },
                color = MegaDriveDim, fontSize = 10.sp, fontFamily = MonoFontFamily
            )

            // ── USE TMUX ──────────────────────────────────────────────────
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("USE TMUX", color = MegaDrivePrimary, fontSize = 12.sp, fontFamily = MonoFontFamily)
                Checkbox(
                    checked = state.useTmux && state.targetType == ProjectTargetType.SSH,
                    onCheckedChange = { viewModel.update { copy(useTmux = it) } },
                    enabled = state.targetType == ProjectTargetType.SSH,
                    colors = CheckboxDefaults.colors(
                        checkedColor = MegaDrivePrimary,
                        uncheckedColor = MegaDriveDim,
                        checkmarkColor = MegaDriveBg
                    )
                )
            }
            Text(
                if (state.targetType == ProjectTargetType.LOCAL)
                    "Local mode currently uses a plain device shell without tmux."
                else if (state.useTmux)
                    "Creates a tmux session named after the project, or attaches if it already exists."
                else
                    "Connects to a plain shell without tmux.",
                color = MegaDriveDim, fontSize = 10.sp, fontFamily = MonoFontFamily
            )

            // ── CUSTOM SCRIPT ─────────────────────────────────────────────
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("CUSTOM SCRIPT", color = MegaDrivePrimary, fontSize = 12.sp, fontFamily = MonoFontFamily)
                RetroButton(
                    text = "[ RESET ]",
                    onClick = { viewModel.update { copy(customScript = "cd {{PROJECT_PATH}}") } }
                )
            }
            Text(
                "Runs inside the session after connect. Placeholders: {{PROJECT_PATH}}, {{SESSION_NAME}}",
                color = MegaDriveDim, fontSize = 10.sp, fontFamily = MonoFontFamily
            )
            OutlinedTextField(
                value = state.customScript,
                onValueChange = { viewModel.update { copy(customScript = it) } },
                modifier = Modifier.fillMaxWidth().height(100.dp),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = MonoFontFamily, fontSize = 12.sp, color = MegaDriveOnSurface
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MegaDrivePrimary,
                    unfocusedBorderColor = MegaDriveDim,
                    focusedTextColor = MegaDriveOnSurface,
                    unfocusedTextColor = MegaDriveOnSurface,
                    cursorColor = MegaDrivePrimary
                )
            )

            // ── AI TOOL ───────────────────────────────────────────────────
            Spacer(Modifier.height(4.dp))
            Text("AI TOOL", color = MegaDrivePrimary, fontSize = 12.sp, fontFamily = MonoFontFamily)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AI_TOOLS.forEach { (label, cmd) ->
                    val selected = state.aiCommand == cmd
                    RetroButton(
                        text = if (selected) "[$label]" else label,
                        onClick = { viewModel.update { copy(aiCommand = cmd) } },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Text(
                "Command to run last in the session:",
                color = MegaDriveDim, fontSize = 10.sp, fontFamily = MonoFontFamily
            )
            OutlinedTextField(
                value = state.aiCommand,
                onValueChange = { viewModel.update { copy(aiCommand = it) } },
                placeholder = { Text("e.g. claude --dangerously-skip-permissions", color = MegaDriveDim, fontSize = 11.sp, fontFamily = MonoFontFamily) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = MonoFontFamily, fontSize = 12.sp, color = MegaDriveOnSurface
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MegaDrivePrimary,
                    unfocusedBorderColor = MegaDriveDim,
                    focusedTextColor = MegaDriveOnSurface,
                    unfocusedTextColor = MegaDriveOnSurface,
                    cursorColor = MegaDrivePrimary
                ),
                singleLine = true
            )

            // ── SAVE ──────────────────────────────────────────────────────
            Spacer(Modifier.height(8.dp))
            RetroButton(
                text = "[ SAVE ]",
                onClick = { viewModel.save() },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.name.isNotBlank() &&
                    (state.targetType == ProjectTargetType.LOCAL || state.selectedServerId != null)
            )
        }
    }
}
