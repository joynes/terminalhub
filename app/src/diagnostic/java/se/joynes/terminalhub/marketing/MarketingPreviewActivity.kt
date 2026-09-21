package se.joynes.terminalhub.marketing

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import se.joynes.terminalhub.ui.components.PixelProgressBar
import se.joynes.terminalhub.ui.components.RetroCard
import se.joynes.terminalhub.ui.components.RetroTopBar
import se.joynes.terminalhub.ui.components.TerminalHubAboutDialog
import se.joynes.terminalhub.ui.navigation.SessionTabBar
import se.joynes.terminalhub.ui.screen.download.DownloadState
import se.joynes.terminalhub.ui.screen.download.DownloadedRemoteFile
import se.joynes.terminalhub.ui.screen.download.FileDownloadViewModel
import se.joynes.terminalhub.ui.screen.download.FloatingFileDownloadDialog
import se.joynes.terminalhub.ui.screen.settings.KeyBarSettingsEditor
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
 * --es scene sessions2|sessions10|upload-multiple|download-multiple|keybar-settings
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

private val manyDemoTabs = listOf(
    "MOBILE", "API", "DOCS", "OPS", "WEB", "TEST", "BUILD", "DATA", "LAB", "PROD"
).mapIndexed { index, name ->
    ProjectTabState(
        projectId = (index + 1).toLong(),
        projectName = name,
        sessionId = TerminalSessionId(name.lowercase()),
        isConnected = true,
        colorSeed = 40 + index * 31,
        usesTmux = true
    )
}

@Composable
private fun MarketingScene(scene: String) {
    when (scene) {
        "servers" -> DemoServers()
        "keybar-settings" -> DemoKeyBarSettings()
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
    val initialTabs = when (scene) {
        "sessions2" -> demoTabs.take(2)
        "sessions10" -> manyDemoTabs
        else -> demoTabs
    }
    var orderedTabs by remember(scene) { mutableStateOf(initialTabs) }
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
        "files", "upload-multiple", "download-multiple" -> """
            $ pwd
            /srv/mobile-app

            $ git status --short
             M src/screens/settings.kt

            Upload requirements.md from Android
            without leaving this project tab.

            $
        """.trimIndent()
        "sessions2" -> """
            ${esc}[36mTerminalHub / project-tabs${esc}[0m

            Two projects are open across two servers.

            ${esc}[32mMOBILE${esc}[0m  workstation ~/projects/mobile
            ${esc}[35mAPI${esc}[0m     vps /srv/api

            Switch tabs; each project keeps its own SSH/tmux session.
            Your work is ready when you return.

            $
        """.trimIndent()
        "sessions10" -> """
            ${esc}[36mTerminalHub / project-tabs${esc}[0m

            Ten projects are open across your servers.

            ${esc}[32mMOBILE${esc}[0m  workstation ~/projects/mobile
            ${esc}[35mAPI${esc}[0m     vps /srv/api
            ${esc}[33mDOCS${esc}[0m    home-lab ~/docs
            ${esc}[34mOPS${esc}[0m     vps /srv/operations
            WEB     workstation ~/projects/web
            TEST    workstation ~/projects/test
            BUILD   workstation ~/projects/build
            DATA    home-lab ~/data
            LAB     home-lab ~/lab
            PROD    vps /srv/production

            Switch instantly between every live terminal session.

            $
        """.trimIndent()
        else -> """
            ${esc}[36mTerminalHub / project-tabs${esc}[0m

            ${orderedTabs.size} projects are open across your servers.

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
                tabs = orderedTabs,
                activeId = activeId,
                onSelect = {},
                onClose = { _, _ -> },
                onReorder = { ids ->
                    val tabsById = orderedTabs.associateBy { it.projectId }
                    orderedTabs = ids.mapNotNull(tabsById::get)
                },
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
                    text = if (scene == "resume") "TMUX RESTORED" else "${orderedTabs.size} ACTIVE",
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
                onUploadsCompleted = {},
                onDismiss = {}
            )
        }

        if (scene == "upload-multiple") {
            DemoMultiFileUploadPanel()
        }

        if (scene == "download-multiple") {
            val viewModel: FileDownloadViewModel = androidx.hilt.navigation.compose.hiltViewModel()
            FloatingFileDownloadDialog(
                viewModel = viewModel,
                projectId = 1,
                serverId = 1,
                downloadState = DownloadState.BatchDone(
                    listOf(
                        DownloadedRemoteFile("README.md", 8_421, Uri.parse("content://terminalhub.demo/README.md")),
                        DownloadedRemoteFile("architecture.png", 284_103, Uri.parse("content://terminalhub.demo/architecture.png")),
                        DownloadedRemoteFile("release-notes.pdf", 153_920, Uri.parse("content://terminalhub.demo/release-notes.pdf"))
                    )
                ),
                onDismiss = {}
            )
        }
    }
}

@Composable
private fun DemoMultiFileUploadPanel() {
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .offset(x = 16.dp, y = 80.dp)
                .fillMaxWidth(0.92f)
                .background(MegaDriveSurface, RoundedCornerShape(4.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .background(MegaDrivePrimary, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("FILE UPLOAD", color = MegaDriveBg, fontSize = 11.sp, fontFamily = MonoFontFamily)
                Text("✕", color = MegaDriveBg, fontSize = 13.sp, fontFamily = MonoFontFamily)
            }
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "architecture.png",
                    color = MegaDrivePrimary,
                    fontSize = 11.sp,
                    fontFamily = MonoFontFamily
                )
                PixelProgressBar(
                    progress = 0.72f,
                    label = "UPLOADING (2/3)...",
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "3 files selected · README.md · architecture.png · release-notes.pdf",
                    color = MegaDriveDim,
                    fontSize = 10.sp,
                    fontFamily = MonoFontFamily,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
private fun DemoKeyBarSettings() {
    var rows by remember {
        mutableStateOf(
            listOf(
                listOf("ESC", "TAB", "CTRL", "ALT", "SLASH", "PIPE", "UP", "ENTER"),
                listOf("TEXT_INPUT", "UPLOAD", "DOWNLOAD", "LEFT", "DOWN", "RIGHT")
            )
        )
    }
    var highlighted by remember { mutableStateOf(setOf("TEXT_INPUT", "UPLOAD", "DOWNLOAD")) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MegaDriveBg)
            .statusBarsPadding()
    ) {
        RetroTopBar(title = "KEY BAR SETTINGS", onBack = null)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "CUSTOMIZE YOUR TERMINAL CONTROLS",
                color = MegaDrivePrimary,
                fontFamily = MonoFontFamily,
                fontSize = 13.sp
            )
            Text(
                "Replace, highlight and reorder the keys you use most.",
                color = MegaDriveDim,
                fontFamily = MonoFontFamily,
                fontSize = 11.sp
            )
            KeyBarSettingsEditor(
                rows = rows,
                highlightedKeyIds = highlighted,
                onRowsChange = { rows = it },
                onHighlightedKeyIdsChange = { highlighted = it }
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
