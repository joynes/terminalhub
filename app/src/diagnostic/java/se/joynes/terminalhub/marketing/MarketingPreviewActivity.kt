package se.joynes.terminalhub.marketing

import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import se.joynes.terminalhub.BuildConfig
import se.joynes.terminalhub.data.model.ProjectTargetType
import se.joynes.terminalhub.data.ssh.RemoteFileEntry
import se.joynes.terminalhub.domain.TerminalSessionId
import se.joynes.terminalhub.ui.components.NeonStatusBadge
import se.joynes.terminalhub.ui.components.RetroCard
import se.joynes.terminalhub.ui.components.RetroTopBar
import se.joynes.terminalhub.ui.components.TerminalHubAboutDialog
import se.joynes.terminalhub.ui.navigation.SessionTabBar
import se.joynes.terminalhub.ui.screen.download.DownloadState
import se.joynes.terminalhub.ui.screen.download.FileDownloadViewModel
import se.joynes.terminalhub.ui.screen.download.FloatingFileDownloadDialog
import se.joynes.terminalhub.ui.screen.sessions.FloatingTextInputDialog
import se.joynes.terminalhub.ui.screen.sessions.ProjectTabState
import se.joynes.terminalhub.ui.screen.terminal.MutableModifierManager
import se.joynes.terminalhub.ui.screen.terminal.SpecialKeyBar
import se.joynes.terminalhub.ui.screen.terminal.TerminalViewClientImpl
import se.joynes.terminalhub.ui.theme.MegaDriveAccent
import se.joynes.terminalhub.ui.theme.MegaDriveBg
import se.joynes.terminalhub.ui.theme.MegaDriveDim
import se.joynes.terminalhub.ui.theme.MegaDriveOnSurface
import se.joynes.terminalhub.ui.theme.MegaDrivePrimary
import se.joynes.terminalhub.ui.theme.MegaDriveSurface
import se.joynes.terminalhub.ui.theme.MonoFontFamily
import se.joynes.terminalhub.ui.theme.TerminalHubTheme

/**
 * Deterministic, diagnostic-only scenes used to capture real TerminalHub UI for store assets.
 * Launch with: adb shell am start -n se.joynes.terminalhub.diag/.marketing.MarketingPreviewActivity
 * --es scene sessions|resume|prompt|files|servers
 */
@AndroidEntryPoint
class MarketingPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.decorView.isForceDarkAllowed = false
        }
        window.decorView.setBackgroundColor(android.graphics.Color.parseColor("#0D0D1A"))
        val scene = intent.getStringExtra("scene") ?: "sessions"
        setContent {
            TerminalHubTheme {
                MarketingScene(scene)
            }
        }
    }
}

private val demoTabs = listOf(
    ProjectTabState(1, "CODEX", TerminalSessionId("codex"), true, colorSeed = 110, usesTmux = true),
    ProjectTabState(2, "CLAUDE", TerminalSessionId("claude"), true, colorSeed = 220, usesTmux = true),
    ProjectTabState(3, "GEMINI", TerminalSessionId("gemini"), true, colorSeed = 330, usesTmux = true),
    ProjectTabState(4, "SHELL", TerminalSessionId("shell"), true, colorSeed = 45, usesTmux = true),
    ProjectTabState(
        5,
        "LOCAL",
        null,
        false,
        colorSeed = 170,
        targetType = ProjectTargetType.LOCAL
    )
)

@Composable
private fun MarketingScene(scene: String) {
    when (scene) {
        "servers" -> DemoServers()
        "opensource" -> Box {
            DemoTerminalWorkspace("sessions")
            TerminalHubAboutDialog(
                onDismiss = {},
                versionLabel = "Version ${BuildConfig.VERSION_NAME.removeSuffix("-diag")} " +
                    "(${BuildConfig.VERSION_CODE})"
            )
        }
        else -> DemoTerminalWorkspace(scene)
    }
}

@Composable
private fun DemoTerminalWorkspace(scene: String) {
    val activeId = if (scene == "resume") TerminalSessionId("claude") else TerminalSessionId("codex")
    val esc = "\u001B"
    val output = when (scene) {
        "resume" -> """
            ${esc}[36mTerminalHub reconnecting...${esc}[0m
            SSH transport restored
            tmux session attached: claude/mobile-release

            ${esc}[32mclaude${esc}[0m  Finished reviewing the release workflow.
            ✓ Android tests passed
            ✓ Play bundle assembled
            ✓ Session survived the network change

            $
        """.trimIndent()
        "files" -> """
            $ pwd
            /workspace/terminalhub

            $ git status --short
             M README.md
             M app/src/main/AndroidManifest.xml

            Upload phone-context.md or download build output
            without leaving the active project.

            $
        """.trimIndent()
        else -> """
            ${esc}[36mTerminalHub / mobile-control${esc}[0m

            Four tmux-backed sessions are running on your workstation.

            ${esc}[32mCODEX${esc}[0m   implementing Android UI changes
            ${esc}[35mCLAUDE${esc}[0m  reviewing the release checklist
            ${esc}[33mGEMINI${esc}[0m  researching test coverage
            ${esc}[34mSHELL${esc}[0m   watching the Gradle build

            Switch tabs to guide each session from your phone.
            No need to sit at your computer.

            $
        """.trimIndent()
    }.replace("\n", "\r\n")
    val session = remember(output) { createDemoSession() }
    val modifierManager = remember { MutableModifierManager() }
    var prompt by remember {
        mutableStateOf(
            TextFieldValue(
                "Review the failing Android test, fix the root cause, run the relevant suite, " +
                    "and summarize exactly what changed."
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MegaDriveBg)
            .statusBarsPadding()
    ) {
        Column(Modifier.fillMaxSize()) {
            SessionTabBar(
                tabs = demoTabs,
                activeId = activeId,
                onSelect = {},
                onClose = { _, _ -> },
                onMove = { _, _ -> },
                onAddProject = {}
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MegaDriveSurface)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (scene == "resume") "AI WORKSTATION / CLAUDE" else "AI WORKSTATION / CODEX",
                    color = MegaDriveOnSurface,
                    fontFamily = MonoFontFamily,
                    fontSize = 10.sp
                )
                NeonStatusBadge(
                    text = if (scene == "resume") "TMUX RESTORED" else "4 ACTIVE",
                    color = MegaDrivePrimary
                )
            }
            DemoTerminal(session, output, Modifier.weight(1f))
            SpecialKeyBar(
                modifierManager = modifierManager,
                onKey = {},
                onTextInput = {},
                onFileUpload = {},
                onFileDownload = {}
            )
            Spacer(Modifier.height(4.dp))
        }

        if (scene == "prompt") {
            FloatingTextInputDialog(
                text = prompt,
                onTextChange = { prompt = it },
                onSend = {},
                onDismiss = {},
                history = listOf(
                    "Run the tests and explain any failures.",
                    "Check the current diff for security issues."
                ),
                bottomAvoidanceDp = 72.dp
            )
        }

        if (scene == "files") {
            val viewModel: FileDownloadViewModel = androidx.hilt.navigation.compose.hiltViewModel()
            FloatingFileDownloadDialog(
                viewModel = viewModel,
                projectId = 1,
                serverId = 1,
                downloadState = DownloadState.Listed(
                    listOf(
                        RemoteFileEntry("build-report.md", 18_432),
                        RemoteFileEntry("test-results.zip", 284_672),
                        RemoteFileEntry("release-notes.txt", 3_148)
                    )
                ),
                onDismiss = {}
            )
        }
    }
}

@Composable
private fun DemoTerminal(session: TerminalSession, output: String, modifier: Modifier = Modifier) {
    var terminalView by remember { mutableStateOf<TerminalView?>(null) }

    LaunchedEffect(session, terminalView) {
        val view = terminalView ?: return@LaunchedEffect
        delay(250)
        val bytes = output.toByteArray(Charsets.UTF_8)
        session.appendRemoteOutput(bytes, 0, bytes.size)
        delay(150)
        view.onScreenUpdated(true)
    }

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            val client = TerminalViewClientImpl(
                modifierManager = MutableModifierManager(),
                onSendToSsh = {},
                onTerminalTap = {}
            )
            TerminalView(ctx, null).apply {
                setLayerType(View.LAYER_TYPE_NONE, null)
                setTextSize((11 * ctx.resources.displayMetrics.scaledDensity + 0.5f).toInt())
                setTerminalViewClient(client)
                attachSession(session)
                setBackgroundColor(0xFF0D0D1A.toInt())
                setCanvasBackgroundColor(0xFF0D0D1A.toInt())
            }.also { terminalView = it }
        },
        update = { view ->
            terminalView = view
            if (view.mTermSession !== session) view.attachSession(session)
        }
    )
}

@Composable
private fun DemoServers() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MegaDriveBg)
            .statusBarsPadding()
    ) {
        RetroTopBar(title = "SERVERS", onBack = null)
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Your AI sessions keep running on the machines you already control.",
                color = MegaDriveOnSurface,
                fontFamily = MonoFontFamily,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            DemoServerCard("AI WORKSTATION", "developer@workstation.example", "4 PROJECTS", MegaDrivePrimary)
            DemoServerCard("HOME LAB", "admin@homelab.example", "3 PROJECTS", MegaDriveAccent)
            DemoServerCard("VPS", "ops@vps.example", "2 PROJECTS", Color(0xFF8BD5CA))
            Spacer(Modifier.height(4.dp))
            RetroCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("OPEN SOURCE", color = MegaDrivePrimary, fontFamily = MonoFontFamily, fontSize = 14.sp)
                    Text(
                        "TerminalHub is GPL-3.0 licensed. Inspect the code, build it yourself, and contribute on GitHub.",
                        color = MegaDriveOnSurface,
                        fontFamily = MonoFontFamily,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun DemoServerCard(name: String, endpoint: String, projects: String, badgeColor: Color) {
    RetroCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(name, color = MegaDrivePrimary, fontFamily = MonoFontFamily, fontSize = 14.sp)
                NeonStatusBadge("SSH READY", badgeColor)
            }
            Text(endpoint, color = MegaDriveOnSurface, fontFamily = MonoFontFamily, fontSize = 12.sp)
            Text(projects, color = MegaDriveDim, fontFamily = MonoFontFamily, fontSize = 11.sp)
        }
    }
}

private fun createDemoSession(): TerminalSession {
    return TerminalSession.createRemoteSession(
        5_000,
        object : com.termux.terminal.TerminalSessionClient {
            override fun onTextChanged(changedSession: TerminalSession) = Unit
            override fun onTitleChanged(changedSession: TerminalSession) = Unit
            override fun onSessionFinished(finishedSession: TerminalSession) = Unit
            override fun onCopyTextToClipboard(session: TerminalSession, text: String) = Unit
            override fun onPasteTextFromClipboard(session: TerminalSession?) = Unit
            override fun onBell(session: TerminalSession) = Unit
            override fun onColorsChanged(session: TerminalSession) = Unit
            override fun onTerminalCursorStateChange(state: Boolean) = Unit
            override fun setTerminalShellPid(session: TerminalSession, pid: Int) = Unit
            override fun getTerminalCursorStyle(): Int = 0
            override fun logError(tag: String?, message: String?) = Unit
            override fun logWarn(tag: String?, message: String?) = Unit
            override fun logInfo(tag: String?, message: String?) = Unit
            override fun logDebug(tag: String?, message: String?) = Unit
            override fun logVerbose(tag: String?, message: String?) = Unit
            override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) = Unit
            override fun logStackTrace(tag: String?, e: Exception?) = Unit
        }
    ) { _, _, _ -> true }
}
