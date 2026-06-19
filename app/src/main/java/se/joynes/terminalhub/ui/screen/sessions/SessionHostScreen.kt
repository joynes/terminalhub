package se.joynes.terminalhub.ui.screen.sessions

import android.net.Uri
import android.os.Build
import android.view.WindowInsets as AndroidWindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.hilt.navigation.compose.hiltViewModel
import se.joynes.terminalhub.R
import se.joynes.terminalhub.BuildConfig
import se.joynes.terminalhub.ui.screen.export.ExportImportState
import se.joynes.terminalhub.ui.screen.export.ExportImportViewModel
import se.joynes.terminalhub.ui.screen.download.DownloadState
import se.joynes.terminalhub.ui.screen.download.FileDownloadViewModel
import se.joynes.terminalhub.ui.screen.download.FloatingFileDownloadDialog
import se.joynes.terminalhub.ui.screen.upload.FileUploadViewModel
import se.joynes.terminalhub.ui.screen.upload.FloatingFileUploadDialog
import se.joynes.terminalhub.ui.screen.upload.UploadState
import com.termux.view.TerminalView
import se.joynes.terminalhub.ui.components.RetroButton
import se.joynes.terminalhub.ui.navigation.SessionTabBar
import se.joynes.terminalhub.ui.screen.terminal.MutableModifierManager
import se.joynes.terminalhub.ui.screen.terminal.SpecialKeyBar
import se.joynes.terminalhub.ui.screen.terminal.TerminalSearchOverlay
import se.joynes.terminalhub.ui.screen.terminal.TerminalViewClientImpl
import se.joynes.terminalhub.ui.theme.*

private val KeyBarReservedHeight = 70.dp

private data class PendingTabClose(
    val projectId: Long,
    val projectName: String,
    val sessionId: se.joynes.terminalhub.domain.TerminalSessionId?
)

@Composable
fun SessionHostScreen(
    requestedServerId: Long? = null,
    onOpenServers: () -> Unit,
    onAddServer: () -> Unit,
    onAddProject: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenSettings: () -> Unit,
    sharedUri: Uri? = null,
    onConsumeSharedUri: () -> Unit = {},
    viewModel: SessionHostViewModel = hiltViewModel()
) {
    val projectTabs by viewModel.projectTabs.collectAsState()
    val sessions by viewModel.sessionManager.sessions.collectAsState()
    val activeId by viewModel.activeId.collectAsState()
    val session by viewModel.activeSession.collectAsState()
    val serverId by viewModel.serverId.collectAsState()
    val runtimeState by viewModel.runtimeState.collectAsState()
    val closedSessions by viewModel.sessionManager.closedSessions.collectAsState()
    val preferFastResume by viewModel.preferFastResume.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val bottomBarReservedHeight = KeyBarReservedHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    var keyboardVisible by remember { mutableStateOf(false) }
    var showSessionHistory by remember { mutableStateOf(false) }
    var showSettingsMenu by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var pendingTabClose by remember { mutableStateOf<PendingTabClose?>(null) }
    var deleteProjectOnClose by remember(pendingTabClose?.projectId) { mutableStateOf(false) }
    val textInputVisibleByProject = remember { mutableStateMapOf<Long, Boolean>() }
    val textInputDraftByProject = remember { mutableStateMapOf<Long, String>() }
    val fileUploadVisibleByProject = remember { mutableStateMapOf<Long, Boolean>() }
    val fileUploadSelectedUriByProject = remember { mutableStateMapOf<Long, Uri?>() }
    val fileUploadSelectedNameByProject = remember { mutableStateMapOf<Long, String>() }
    val fileDownloadVisibleByProject = remember { mutableStateMapOf<Long, Boolean>() }
    var searchVisible by remember { mutableStateOf(false) }
    var searchInitialQuery by remember { mutableStateOf("") }
    var isTerminalAtBottom by remember { mutableStateOf(true) }
    val fileUploadViewModel: FileUploadViewModel = hiltViewModel()
    val fileDownloadViewModel: FileDownloadViewModel = hiltViewModel()
    val exportImportViewModel: ExportImportViewModel = hiltViewModel()
    val exportImportState by exportImportViewModel.state.collectAsState()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri?.let {
            exportImportViewModel.export(
                context,
                it,
                activeProjectIds = projectTabs.map { tab -> tab.projectId }.toSet()
            )
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { exportImportViewModel.import(context, it) } }

    LaunchedEffect(exportImportState) {
        when (val s = exportImportState) {
            is ExportImportState.ExportDone -> {
                Toast.makeText(context, "Config exported", Toast.LENGTH_SHORT).show()
                exportImportViewModel.resetState()
            }
            is ExportImportState.ImportDone -> {
                Toast.makeText(
                    context,
                    "Imported ${s.result.servers} server(s), ${s.result.projects} project(s)",
                    Toast.LENGTH_LONG
                ).show()
                exportImportViewModel.resetState()
            }
            is ExportImportState.Error -> {
                Toast.makeText(context, "Error: ${s.message}", Toast.LENGTH_LONG).show()
                exportImportViewModel.resetState()
            }
            else -> {}
        }
    }

    val activeProjectId = remember(activeId, projectTabs) {
        projectTabs.firstOrNull { it.sessionId == activeId }?.projectId
    }
    val activeTab = remember(activeProjectId, projectTabs) {
        activeProjectId?.let { projectId -> projectTabs.firstOrNull { it.projectId == projectId } }
    }
    val restoringTab = remember(projectTabs, runtimeState.recoveryActiveProjectId) {
        val recoveryId = runtimeState.recoveryActiveProjectId
        projectTabs.firstOrNull { it.projectId == recoveryId && it.isConnecting }
            ?: projectTabs.firstOrNull { it.isConnecting }
    }
    val canReconnectActiveTab = activeTab != null &&
        activeTab.targetType == se.joynes.terminalhub.data.model.ProjectTargetType.SSH
    val activeTextInputVisible = activeProjectId?.let { textInputVisibleByProject[it] == true } ?: false
    val activeTextInputDraft = activeProjectId?.let { textInputDraftByProject[it].orEmpty() }.orEmpty()
    val activeFileUploadVisible = activeProjectId?.let { fileUploadVisibleByProject[it] == true } ?: false
    val activeFileUploadSelectedUri = activeProjectId?.let { fileUploadSelectedUriByProject[it] }
    val activeFileUploadSelectedName = activeProjectId?.let { fileUploadSelectedNameByProject[it].orEmpty() }.orEmpty()
    val activeFileDownloadVisible = activeProjectId?.let { fileDownloadVisibleByProject[it] == true } ?: false
    val textInputHistory by remember(activeProjectId) {
        activeProjectId?.let { viewModel.textInputHistory(it) } ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())
    val fileUploadState by remember(activeProjectId) {
        activeProjectId?.let { fileUploadViewModel.uploadState(it) } ?: kotlinx.coroutines.flow.flowOf(UploadState.Idle)
    }.collectAsState(initial = UploadState.Idle)
    val fileDownloadState by remember(activeProjectId) {
        activeProjectId?.let { fileDownloadViewModel.downloadState(it) } ?: kotlinx.coroutines.flow.flowOf(DownloadState.Idle)
    }.collectAsState(initial = DownloadState.Idle)

    // Shared modifier manager: toggles in SpecialKeyBar are read by TerminalViewClientImpl
    val modifierManager = remember { MutableModifierManager() }

    // Reference to the live TerminalView for direct IMM calls
    val terminalViewRef = remember { mutableStateOf<TerminalView?>(null) }

    // Refresh scroll-to-bottom button visibility whenever the view's top row changes.
    LaunchedEffect(terminalViewRef.value) {
        val tv = terminalViewRef.value
        if (tv != null) {
            isTerminalAtBottom = tv.isAtBottom()
            tv.setOnTopRowChangedListener { isTerminalAtBottom = tv.isAtBottom() }
        } else {
            isTerminalAtBottom = true
        }
    }
    var lastSyncedCols by remember { mutableIntStateOf(-1) }
    var lastSyncedRows by remember { mutableIntStateOf(-1) }
    var lastViewWidth by remember { mutableIntStateOf(-1) }
    var lastViewHeight by remember { mutableIntStateOf(-1) }

    fun showKeyboard() {
        val tv = terminalViewRef.value ?: return
        tv.requestFocusFromTouch()
        tv.requestFocus()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            tv.windowInsetsController?.show(AndroidWindowInsets.Type.ime())
        } else {
            context.getSystemService(InputMethodManager::class.java)
                ?.showSoftInput(tv, InputMethodManager.SHOW_FORCED)
        }
    }

    fun hideKeyboard() {
        val tv = terminalViewRef.value ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            tv.windowInsetsController?.hide(AndroidWindowInsets.Type.ime())
        } else {
            context.getSystemService(InputMethodManager::class.java)
                ?.hideSoftInputFromWindow(tv.windowToken, 0)
        }
    }

    fun syncRemotePty(tv: TerminalView) {
        tv.setBackgroundColor(0xFF0D0D1A.toInt())
        tv.setCanvasBackgroundColor(0xFF0D0D1A.toInt())
        val viewChanged = tv.width != lastViewWidth || tv.height != lastViewHeight
        if (viewChanged) {
            lastViewWidth = tv.width
            lastViewHeight = tv.height
            tv.updateSize()
        }
        val emulator = tv.mEmulator ?: return
        val ptyChanged = emulator.mColumns != lastSyncedCols || emulator.mRows != lastSyncedRows
        if (ptyChanged) {
            lastSyncedCols = emulator.mColumns
            lastSyncedRows = emulator.mRows
            viewModel.resizeActivePty(emulator.mColumns, emulator.mRows)
        }
        if (viewChanged || ptyChanged) {
            tv.onScreenUpdated(true)
        }
    }

    LaunchedEffect(sharedUri) {
        val projectId = activeProjectId
        if (sharedUri != null && projectId != null) {
            fileUploadSelectedUriByProject[projectId] = sharedUri
            textInputVisibleByProject[projectId] = false
            fileDownloadVisibleByProject[projectId] = false
            fileUploadVisibleByProject[projectId] = true
            onConsumeSharedUri()
        }
    }

    LaunchedEffect(requestedServerId) {
        viewModel.selectServer(requestedServerId)
        viewModel.init()
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.screenUpdates.collect { changedSession ->
                val tv = terminalViewRef.value ?: return@collect
                if (tv.mTermSession === changedSession) {
                    tv.onScreenUpdated()
                }
            }
        }
    }

    // Re-request focus when the app comes back from background
    val currentSession by rememberUpdatedState(session)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val tv = terminalViewRef.value
            if (event == Lifecycle.Event.ON_RESUME && currentSession != null && preferFastResume) {
                keyboardVisible = true
                tv?.requestFocus()
            } else if (event == Lifecycle.Event.ON_STOP) {
                keyboardVisible = false
                hideKeyboard()
                tv?.clearFocus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showSessionHistory) {
        SessionHistorySheet(
            sessions = sessions,
            closedSessions = closedSessions,
            activeId = activeId,
            onSelect = { viewModel.switchToSession(it); terminalViewRef.value?.requestFocus() },
            onReopen = { projectId -> viewModel.reopenSession(projectId) },
            onDismiss = { showSessionHistory = false }
        )
    }
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            containerColor = MegaDriveSurface,
            title = {
                Text(
                    "ABOUT",
                    color = MegaDrivePrimary,
                    fontFamily = MonoFontFamily,
                    fontSize = 14.sp
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground_image),
                        contentDescription = "App icon",
                        modifier = Modifier
                            .size(72.dp)
                            .background(MegaDriveBg)
                            .padding(6.dp)
                    )
                    Text(
                        "AI TERMINAL HUB",
                        color = Color.White,
                        fontFamily = MonoFontFamily,
                        fontSize = 13.sp
                    )
                    Text(
                        "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        color = MegaDriveDim,
                        fontFamily = MonoFontFamily,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                RetroButton(
                    text = "CLOSE",
                    onClick = { showAboutDialog = false }
                )
            }
        )
    }
    pendingTabClose?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingTabClose = null },
            containerColor = MegaDriveSurface,
            title = {
                Text(
                    "CLOSE TMUX SESSION?",
                    color = MegaDrivePrimary,
                    fontFamily = MonoFontFamily,
                    fontSize = 14.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Project ${pending.projectName} uses tmux. Close only the tab, or also kill the remote tmux session?",
                        color = Color.White,
                        fontFamily = MonoFontFamily,
                        fontSize = 12.sp
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { deleteProjectOnClose = !deleteProjectOnClose },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = deleteProjectOnClose,
                            onCheckedChange = { deleteProjectOnClose = it }
                        )
                        Text(
                            "Move project to .trash and remove from app",
                            color = Color.White,
                            fontFamily = MonoFontFamily,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            dismissButton = {
                RetroButton(
                    text = "TAB ONLY",
                    onClick = {
                        viewModel.closeProject(
                            pending.projectId,
                            pending.sessionId,
                            killTmuxSession = false,
                            deleteProject = deleteProjectOnClose
                        )
                        pendingTabClose = null
                    }
                )
            },
            confirmButton = {
                RetroButton(
                    text = "KILL TMUX",
                    onClick = {
                        viewModel.closeProject(
                            pending.projectId,
                            pending.sessionId,
                            killTmuxSession = true,
                            deleteProject = deleteProjectOnClose
                        )
                        pendingTabClose = null
                    }
                )
            }
        )
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .background(MegaDriveBg)
    ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 28.dp)
                    .background(MegaDriveSurface)
                    .zIndex(1f)
            ) {
                if (projectTabs.isNotEmpty()) {
                    SessionTabBar(
                        tabs = projectTabs,
                        activeId = activeId,
                        onSelect = { viewModel.switchToSession(it); terminalViewRef.value?.requestFocus() },
                        onClose = { projectId, sessionId ->
                            val tab = projectTabs.firstOrNull { it.projectId == projectId }
                            if (tab?.usesTmux == true && sessionId != null) {
                                pendingTabClose = PendingTabClose(
                                    projectId = projectId,
                                    projectName = tab.projectName,
                                    sessionId = sessionId
                                )
                            } else {
                                viewModel.closeSession(projectId, sessionId)
                            }
                        },
                        onMove = { fromIndex, toIndex -> viewModel.moveSession(fromIndex, toIndex) },
                        onAddProject = onAddProject,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 32.dp)
                    )
                } else {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .height(28.dp)
                        .width(32.dp)
                        .background(MegaDriveSurface)
                        .border(1.dp, MegaDriveBg.copy(alpha = 0.35f))
                        .clickable { showSettingsMenu = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "⚙",
                        color = MegaDrivePrimary,
                        fontSize = 13.sp,
                        fontFamily = MonoFontFamily
                    )
                    DropdownMenu(
                        expanded = showSettingsMenu,
                        onDismissRequest = { showSettingsMenu = false }
                    ) {
                        if (canReconnectActiveTab) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Reconnect",
                                        color = Color.White,
                                        fontFamily = MonoFontFamily,
                                        fontSize = 12.sp
                                    )
                                },
                                onClick = {
                                    showSettingsMenu = false
                                    activeTab?.let { viewModel.reconnectProject(it.projectId) }
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Settings",
                                    color = Color.White,
                                    fontFamily = MonoFontFamily,
                                    fontSize = 12.sp
                                )
                            },
                            onClick = {
                                showSettingsMenu = false
                                onOpenSettings()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Servers",
                                    color = Color.White,
                                    fontFamily = MonoFontFamily,
                                    fontSize = 12.sp
                                )
                            },
                            onClick = {
                                showSettingsMenu = false
                                onOpenServers()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "See Logs",
                                    color = Color.White,
                                    fontFamily = MonoFontFamily,
                                    fontSize = 12.sp
                                )
                            },
                            onClick = {
                                showSettingsMenu = false
                                onOpenLogs()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Recent Projects",
                                    color = Color.White,
                                    fontFamily = MonoFontFamily,
                                    fontSize = 12.sp
                                )
                            },
                            onClick = {
                                showSettingsMenu = false
                                showSessionHistory = true
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Export Config",
                                    color = Color.White,
                                    fontFamily = MonoFontFamily,
                                    fontSize = 12.sp
                                )
                            },
                            onClick = {
                                showSettingsMenu = false
                                exportLauncher.launch("terminalhub_backup.yaml")
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Import Config",
                                    color = Color.White,
                                    fontFamily = MonoFontFamily,
                                    fontSize = 12.sp
                                )
                            },
                            onClick = {
                                showSettingsMenu = false
                                importLauncher.launch(arrayOf("text/plain", "application/yaml", "*/*"))
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "About",
                                    color = Color.White,
                                    fontFamily = MonoFontFamily,
                                    fontSize = 12.sp
                                )
                            },
                            onClick = {
                                showSettingsMenu = false
                                showAboutDialog = true
                            }
                        )
                    }
                }
            }

            if (projectTabs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (serverId == null) {
                            Text(
                                if (BuildConfig.IS_DIAGNOSTIC) "NO LOCAL PROJECTS" else "NO SERVER",
                                color = MegaDriveDim,
                                fontSize = 12.sp,
                                fontFamily = MonoFontFamily
                            )
                            Spacer(Modifier.height(16.dp))
                            RetroButton(
                                if (BuildConfig.IS_DIAGNOSTIC) "[ + ADD PROJECT ]" else "[ + ADD SERVER ]",
                                onAddServer
                            )
                        } else {
                            Text(
                                "NO PROJECTS",
                                color = MegaDriveDim,
                                fontSize = 12.sp,
                                fontFamily = MonoFontFamily
                            )
                            Spacer(Modifier.height(16.dp))
                            RetroButton("[ + ADD PROJECT ]", onAddProject)
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(MegaDriveBg)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = bottomBarReservedHeight)
                    ) {
                        val sess = session
                        if (sess != null) {
                            key(sess) {
                                val terminalViewClient = remember(sess) {
                                    TerminalViewClientImpl(
                                        modifierManager = modifierManager,
                                        onSendToSsh = { bytes -> viewModel.sendBytesToActive(bytes) },
                                        onTerminalTap = {
                                            keyboardVisible = true
                                            showKeyboard()
                                        },
                                        onSearch = { text ->
                                            searchInitialQuery = text
                                            searchVisible = true
                                        }
                                    )
                                }
                                LaunchedEffect(sess) {
                                    keyboardVisible = false
                                }
                                AndroidView(
                                    factory = { ctx ->
                                        val textSizePx = (14 * ctx.resources.displayMetrics.scaledDensity + 0.5f).toInt()
                                        TerminalView(ctx, null).apply {
                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                                isForceDarkAllowed = false
                                            }
                                            setLayerType(android.view.View.LAYER_TYPE_NONE, null)
                                            isFocusable = true
                                            isFocusableInTouchMode = true
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                defaultFocusHighlightEnabled = false
                                            }
                                            setBackgroundColor(0xFF0D0D1A.toInt())
                                            setCanvasBackgroundColor(0xFF0D0D1A.toInt())
                                            setTextSize(textSizePx)
                                            setTerminalViewClient(terminalViewClient)
                                            attachSession(sess)
                                            addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                                                syncRemotePty(view as TerminalView)
                                            }
                                        }.also { tv ->
                                            terminalViewRef.value = tv
                                            tv.post {
                                                syncRemotePty(tv)
                                                tv.requestFocusFromTouch()
                                                tv.requestFocus()
                                            }
                                        }
                                    },
                                    update = { tv ->
                                        if (tv.mTermSession !== sess) {
                                            tv.attachSession(sess)
                                            lastSyncedCols = -1
                                            lastSyncedRows = -1
                                            lastViewWidth = -1
                                            lastViewHeight = -1
                                        }
                                        tv.setLayerType(android.view.View.LAYER_TYPE_NONE, null)
                                        tv.setBackgroundColor(0xFF0D0D1A.toInt())
                                        tv.setCanvasBackgroundColor(0xFF0D0D1A.toInt())
                                        terminalViewRef.value = tv
                                        syncRemotePty(tv)
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                        } else if (restoringTab != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MegaDriveBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (runtimeState.recoveryPending) {
                                        "RESTORING ${restoringTab.projectName.uppercase()}..."
                                    } else {
                                        "CONNECTING..."
                                    },
                                    color = MegaDrivePrimary,
                                    fontSize = 12.sp,
                                    fontFamily = MonoFontFamily
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MegaDriveBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "NO ACTIVE SESSION",
                                    color = MegaDriveDim,
                                    fontSize = 12.sp,
                                    fontFamily = MonoFontFamily
                                )
                            }
                        }

                        if (activeTab != null &&
                            activeTab.targetType == se.joynes.terminalhub.data.model.ProjectTargetType.SSH &&
                            !activeTab.isConnected &&
                            activeTab.sessionId != null
                        ) {
                            val disconnectedCount = projectTabs.count { tab ->
                                tab.targetType == se.joynes.terminalhub.data.model.ProjectTargetType.SSH &&
                                !tab.isConnected && !tab.isConnecting
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 12.dp)
                                    .background(MegaDriveSurface)
                                    .border(1.dp, MegaDriveError.copy(alpha = 0.45f))
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        "SESSION DISCONNECTED",
                                        color = MegaDriveError,
                                        fontSize = 12.sp,
                                        fontFamily = MonoFontFamily
                                    )
                                    RetroButton(
                                        text = "RECONNECT",
                                        onClick = { viewModel.reconnectProject(activeTab.projectId) }
                                    )
                                    if (disconnectedCount > 1) {
                                        RetroButton(
                                            text = "RECONNECT ALL ($disconnectedCount)",
                                            onClick = { viewModel.reconnectAllDisconnected() }
                                        )
                                    }
                                }
                            }
                        }

                        if (activeTextInputVisible && activeProjectId != null) {
                            FloatingTextInputDialog(
                                text = activeTextInputDraft,
                                onTextChange = { textInputDraftByProject[activeProjectId] = it },
                                onSend = { text ->
                                    val payload = if (text.endsWith("\n") || text.endsWith("\r")) text else "$text\r"
                                    viewModel.sendBytesToActive(payload.toByteArray(Charsets.UTF_8))
                                    textInputDraftByProject[activeProjectId] = ""
                                    textInputVisibleByProject[activeProjectId] = false
                                },
                                onDismiss = {
                                    textInputVisibleByProject[activeProjectId] = false
                                    keyboardVisible = true
                                    showKeyboard()
                                },
                                history = textInputHistory,
                                onSaveHistory = { text ->
                                    activeProjectId?.let { viewModel.saveTextInput(it, text) }
                                },
                                bottomAvoidanceDp = bottomBarReservedHeight
                            )
                        }

                        if (activeFileUploadVisible && activeProjectId != null) {
                            FloatingFileUploadDialog(
                                viewModel = fileUploadViewModel,
                                projectId = activeProjectId,
                                serverId = serverId ?: 0L,
                                uploadState = fileUploadState,
                                selectedUri = activeFileUploadSelectedUri,
                                selectedName = activeFileUploadSelectedName,
                                onSelectedUriChange = { fileUploadSelectedUriByProject[activeProjectId] = it },
                                onSelectedNameChange = { fileUploadSelectedNameByProject[activeProjectId] = it },
                                initialUri = activeFileUploadSelectedUri,
                                onDismiss = {
                                    fileUploadVisibleByProject[activeProjectId] = false
                                    terminalViewRef.value?.requestFocus()
                                }
                            )
                        }

                        if (activeFileDownloadVisible && activeProjectId != null) {
                            FloatingFileDownloadDialog(
                                viewModel = fileDownloadViewModel,
                                projectId = activeProjectId,
                                serverId = serverId ?: 0L,
                                downloadState = fileDownloadState,
                                onDismiss = {
                                    fileDownloadVisibleByProject[activeProjectId] = false
                                    terminalViewRef.value?.requestFocus()
                                }
                            )
                        }

                        if (searchVisible) {
                            TerminalSearchOverlay(
                                initialQuery = searchInitialQuery,
                                terminalViewRef = terminalViewRef.value,
                                onDismiss = {
                                    searchVisible = false
                                    terminalViewRef.value?.requestFocus()
                                },
                                modifier = Modifier.align(Alignment.TopStart)
                            )
                        }

                        if (!isTerminalAtBottom) {
                            ScrollToBottomButton(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(bottom = 6.dp, end = 8.dp),
                                onClick = {
                                    terminalViewRef.value?.scrollToBottom()
                                    terminalViewRef.value?.requestFocus()
                                }
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .fillMaxWidth()
                    ) {
                        SpecialKeyBar(
                            modifierManager = modifierManager,
                            onKey = { keyStr ->
                                if (activeTextInputVisible && activeProjectId != null) {
                                    when {
                                        keyStr == "\r" -> {
                                            val draft = textInputDraftByProject[activeProjectId].orEmpty()
                                            if (draft.isNotEmpty()) {
                                                viewModel.saveTextInput(activeProjectId, draft)
                                                val payload = if (draft.endsWith("\n") || draft.endsWith("\r")) draft else "$draft\r"
                                                viewModel.sendBytesToActive(payload.toByteArray(Charsets.UTF_8))
                                                textInputDraftByProject[activeProjectId] = ""
                                                textInputVisibleByProject[activeProjectId] = false
                                            }
                                        }
                                        (keyStr.length == 1 && keyStr[0] >= ' ' && keyStr[0] != '\u007F') || keyStr == "\t" -> {
                                            textInputDraftByProject[activeProjectId] =
                                                textInputDraftByProject[activeProjectId].orEmpty() + keyStr
                                        }
                                    }
                                } else {
                                    viewModel.sendBytesToActive(keyStr.toByteArray(Charsets.UTF_8))
                                }
                            },
                            onPaste = {
                                val text = clipboardManager.getText()?.text ?: return@SpecialKeyBar
                                viewModel.sendBytesToActive(text.toByteArray(Charsets.UTF_8))
                            },
                            onTextInput = {
                                activeProjectId?.let { projectId ->
                                    fileUploadVisibleByProject[projectId] = false
                                    fileDownloadVisibleByProject[projectId] = false
                                    textInputVisibleByProject[projectId] = true
                                }
                            },
                            onFileUpload = {
                                activeProjectId?.let { projectId ->
                                    textInputVisibleByProject[projectId] = false
                                    fileDownloadVisibleByProject[projectId] = false
                                    fileUploadSelectedUriByProject[projectId] = null
                                    fileUploadSelectedNameByProject[projectId] = ""
                                    fileUploadViewModel.reset(projectId)
                                    fileUploadVisibleByProject[projectId] = true
                                }
                            },
                            onFileDownload = {
                                activeProjectId?.let { projectId ->
                                    textInputVisibleByProject[projectId] = false
                                    fileUploadVisibleByProject[projectId] = false
                                    fileDownloadViewModel.reset(projectId)
                                    fileDownloadVisibleByProject[projectId] = true
                                }
                            },
                            onKeyboardToggle = {
                                keyboardVisible = !keyboardVisible
                                if (keyboardVisible) showKeyboard() else hideKeyboard()
                            },
                            onPrevTab = {
                                val connected = projectTabs.filter { it.sessionId != null }
                                val curIdx = connected.indexOfFirst { it.sessionId == activeId }
                                if (curIdx > 0) {
                                    connected[curIdx - 1].sessionId?.let {
                                        viewModel.switchToSession(it)
                                        terminalViewRef.value?.requestFocus()
                                    }
                                }
                            },
                            onNextTab = {
                                val connected = projectTabs.filter { it.sessionId != null }
                                val curIdx = connected.indexOfFirst { it.sessionId == activeId }
                                if (curIdx in 0 until connected.size - 1) {
                                    connected[curIdx + 1].sessionId?.let {
                                        viewModel.switchToSession(it)
                                        terminalViewRef.value?.requestFocus()
                                    }
                                }
                            }
                        )
                    }
                }
            }
    }
}

@Composable
private fun ScrollToBottomButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MegaDrivePrimary.copy(alpha = 0.85f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text("↓", color = MegaDriveBg, fontSize = 18.sp, fontFamily = MonoFontFamily)
    }
}
