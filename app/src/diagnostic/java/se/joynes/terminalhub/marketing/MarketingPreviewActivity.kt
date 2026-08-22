package se.joynes.terminalhub.marketing

import android.net.Uri
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
import se.joynes.terminalhub.domain.TerminalSessionId
import se.joynes.terminalhub.ui.components.NeonStatusBadge
import se.joynes.terminalhub.ui.components.RetroCard
import se.joynes.terminalhub.ui.components.RetroTopBar
import se.joynes.terminalhub.ui.components.TerminalHubAboutDialog
import se.joynes.terminalhub.ui.navigation.SessionTabBar
import se.joynes.terminalhub.ui.screen.sessions.FloatingTextInputDialog
import se.joynes.terminalhub.ui.screen.sessions.ProjectTabState
import se.joynes.terminalhub.ui.screen.terminal.MutableModifierManager
import se.joynes.terminalhub.ui.screen.terminal.SpecialKeyBar
import se.joynes.terminalhub.ui.screen.terminal.TerminalViewClientImpl
import se.joynes.terminalhub.ui.screen.upload.FileUploadViewModel
import se.joynes.terminalhub.ui.screen.upload.FloatingFileUploadDialog
import se.joynes.terminalhub.ui.screen.upload.UploadState
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
    ProjectTabState(1, "MOBILE", TerminalSessionId("mobile"), true, colorSeed = 110, usesTmux = true),
    ProjectTabState(2, "API", TerminalSessionId("api"), true, colorSeed = 220, usesTmux = true),
    ProjectTabState(3, "DOCS", TerminalSessionId("docs"), true, colorSeed = 330, usesTmux = true),
    ProjectTabState(4, "OPS", TerminalSessionId("ops"), true, colorSeed = 45, usesTmux = true),
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
    val activeId = if (scene == "resume") TerminalSessionId("api") else TerminalSessionId("mobile")
    val esc = "\u001B"
    val output = when (scene) {
        "resume" -> """
            ${esc}[36mRestoring saved project tab: API${esc}[0m
            SSH transport restored: ops@vps.example
            tmux session attached: api

            ✓ Working directory restored: /srv/api
            ✓ Development server still running
            ✓ Tab order preserved after app restart

            $
        """.trimIndent()
        "files" -> """
            $ pwd
            /srv/mobile-app

            $ git status --short
             M src/screens/settings.kt

            Upload requirements.md from Android
            without leaving this project tab.

            $
        """.trimIndent()
        else -> """
            ${esc}[36mTerminalHub / project-tabs${esc}[0m

            Four projects are open across three servers.

            ${esc}[32mMOBILE${esc}[0m  workstation ~/projects/mobile
            ${esc}[35mAPI${esc}[0m     vps /srv/api
            ${esc}[33mDOCS${esc}[0m    home-lab ~/docs
            ${esc}[34mOPS${esc}[0m     vps /srv/operations

            Switch tabs; every project keeps its own SSH/tmux session.
            Create, clone, and run projects from your phone.

            $
        """.trimIndent()
    }.replace("\n", "\r\n")
    val session = remember(output) { createDemoSession() }
    val modifierManager = remember { MutableModifierManager() }
    var prompt by remember {
        mutableStateOf(
            TextFieldValue(
                "git switch -c feature/mobile-auth\n" +
                    "./gradlew test\n" +
                    "git status --short"
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
                    if (scene == "resume") "VPS / API" else "WORKSTATION / MOBILE",
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
                    "git pull --ff-only && ./gradlew test",
                    "docker compose logs --tail=100 api"
                ),
                bottomAvoidanceDp = 72.dp
            )
        }

        if (scene == "files") {
            val viewModel: FileUploadViewModel = androidx.hilt.navigation.compose.hiltViewModel()
            FloatingFileUploadDialog(
                viewModel = viewModel,
                projectId = 1,
                serverId = 1,
                uploadState = UploadState.Uploading("requirements.md", 0.72f),
                selectedUri = Uri.parse("content://terminalhub.demo/requirements.md"),
                selectedName = "requirements.md",
                onSelectedUriChange = {},
                onSelectedNameChange = {},
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
                "Start and organize projects on every server you control.",
                color = MegaDriveOnSurface,
                fontFamily = MonoFontFamily,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            DemoServerCard("WORKSTATION", "developer@workstation.example", "4 PROJECTS", MegaDrivePrimary)
            DemoServerCard("HOME LAB", "admin@homelab.example", "3 PROJECTS", MegaDriveAccent)
            DemoServerCard("VPS", "ops@vps.example", "2 PROJECTS", Color(0xFF8BD5CA))
            Spacer(Modifier.height(4.dp))
            RetroCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("OPEN SOURCE", color = MegaDrivePrimary, fontFamily = MonoFontFamily, fontSize = 14.sp)
                    Text(
                        "GPL-3.0 licensed, with terminal components adapted from the open-source Termux project.",
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
