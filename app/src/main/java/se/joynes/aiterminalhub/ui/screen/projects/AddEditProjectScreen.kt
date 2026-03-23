package se.joynes.aiterminalhub.ui.screen.projects

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
import se.joynes.aiterminalhub.ui.components.*
import se.joynes.aiterminalhub.ui.theme.*

private val AI_TOOLS = listOf(
    "None"         to "",
    "Claude Code"  to "claude",
    "Gemini CLI"   to "gemini",
    "Openclaw"     to "openclaw tui"
)

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
            // ── Project name ──────────────────────────────────────────────
            RetroTextField(
                state.name,
                { viewModel.update { copy(name = it) } },
                "Project Name *",
                Modifier.fillMaxWidth()
            )
            Text(
                "Path and tmux session are derived from the server's projects folder and this name.",
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
                    checked = state.useTmux,
                    onCheckedChange = { viewModel.update { copy(useTmux = it) } },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MegaDrivePrimary,
                        uncheckedColor = MegaDriveDim,
                        checkmarkColor = MegaDriveBg
                    )
                )
            }
            Text(
                if (state.useTmux)
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
                enabled = state.name.isNotBlank()
            )
        }
    }
}
