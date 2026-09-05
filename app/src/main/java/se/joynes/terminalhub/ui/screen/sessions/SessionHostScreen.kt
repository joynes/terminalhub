package se.joynes.terminalhub.ui.screen.sessions

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.TextFieldValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.core.content.ContextCompat
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
import se.joynes.terminalhub.ui.components.TerminalHubAboutDialog
import se.joynes.terminalhub.ui.navigation.SessionTabBar
import se.joynes.terminalhub.ui.screen.terminal.MutableModifierManager
import se.joynes.terminalhub.ui.screen.terminal.SpecialKeyBar
import se.joynes.terminalhub.ui.screen.terminal.TerminalSearchOverlay
import se.joynes.terminalhub.ui.screen.terminal.TerminalViewClientImpl
import se.joynes.terminalhub.ui.theme.*

private data class PendingTabClose(
    val projectId: Long,
    val projectName: String,
    val sessionId: se.joynes.terminalhub.domain.TerminalSessionId?
)

private data class PendingTmuxRestart(
    val projectId: Long,
    val projectName: String
)

internal enum class TerminalConnectionOverlay {
    NONE,
    PROGRESS,
    DISCONNECTED
}

internal fun terminalConnectionOverlay(
    hasRenderedSession: Boolean,
    hasConnectingRemoteTabs: Boolean,
    activeRemoteTabDisconnected: Boolean
): TerminalConnectionOverlay = when {
    hasRenderedSession && hasConnectingRemoteTabs -> TerminalConnectionOverlay.PROGRESS
    activeRemoteTabDisconnected -> TerminalConnectionOverlay.DISCONNECTED
    else -> TerminalConnectionOverlay.NONE
}

@Composable
fun SessionHostScreen(
    requestedServerId: Long? = null,
    requestedProjectId: Long? = null,
    reconnectAllRequested: Boolean = false,
    onOpenServers: () -> Unit,
    onAddServer: () -> Unit,
    onAddProject: (Long?) -> Unit,
    onOpenLogs: () -> Unit,
    onOpenSettings: () -> Unit,
    sharedUri: Uri? = null,
    onConsumeSharedUri: () -> Unit = {},
    viewModel: SessionHostViewModel = hiltViewModel()
) {
    val projectTabs by viewModel.projectTabs.collectAsState()
    var pendingReconnectAll by remember(reconnectAllRequested) { mutableStateOf(reconnectAllRequested) }
    val sessions by viewModel.sessionManager.sessions.collectAsState()
    val activeId by viewModel.activeId.collectAsState()
    val session by viewModel.activeSession.collectAsState()
    val serverId by viewModel.serverId.collectAsState()
    val homeState by viewModel.homeState.collectAsState()
    val runtimeState by viewModel.runtimeState.collectAsState()
    val hostKeyPrompts by viewModel.hostKeyPrompts.collectAsState()
    val trustingHostKeys by viewModel.trustingHostKeys.collectAsState()
    val showBackgroundSshRecommendation by viewModel.showBackgroundSshRecommendation.collectAsState()
    val closedSessions by viewModel.sessionManager.closedSessions.collectAsState()
    val preferFastResume by viewModel.preferFastResume.collectAsState()
    val executeTextInputOnSend by viewModel.executeTextInputOnSend.collectAsState()
    val keyBarRows by viewModel.keyBarRows.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val keyBarReservedHeight = (keyBarRows.size * 36).dp
    val bottomBarReservedHeight = keyBarReservedHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val imeBottomPx = WindowInsets.ime.getBottom(density)

    var keyboardVisible by remember { mutableStateOf(false) }
    var showSessionHistory by remember { mutableStateOf(false) }
    var showSettingsMenu by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var pendingTabClose by remember { mutableStateOf<PendingTabClose?>(null) }
    var pendingTmuxRestart by remember { mutableStateOf<PendingTmuxRestart?>(null) }
    var deleteProjectOnClose by remember(pendingTabClose?.projectId) { mutableStateOf(false) }
    val textInputVisibleByProject = remember { mutableStateMapOf<Long, Boolean>() }
    val textInputDraftByProject = remember { mutableStateMapOf<Long, TextFieldValue>() }
    val fileUploadVisibleByProject = remember { mutableStateMapOf<Long, Boolean>() }
    val fileUploadSelectedUriByProject = remember { mutableStateMapOf<Long, Uri?>() }
    val fileUploadSelectedNameByProject = remember { mutableStateMapOf<Long, String>() }
    val fileDownloadVisibleByProject = remember { mutableStateMapOf<Long, Boolean>() }
    var searchVisible by remember { mutableStateOf(false) }
    var searchInitialQuery by remember { mutableStateOf("") }
    var isTerminalAtBottom by remember { mutableStateOf(true) }
    var textInputPanelOpacity by rememberSaveable { mutableStateOf(1f) }
    val fileUploadViewModel: FileUploadViewModel = hiltViewModel()
    val fileDownloadViewModel: FileDownloadViewModel = hiltViewModel()
    val exportImportViewModel: ExportImportViewModel = hiltViewModel()
    val exportImportState by exportImportViewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.uiMessages.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val backgroundNotificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.startRecommendedBackgroundSsh(notificationPermissionGranted = granted)
    }

    fun acceptBackgroundSshRecommendation() {
        val notificationGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        if (notificationGranted) {
            viewModel.startRecommendedBackgroundSsh(notificationPermissionGranted = true)
        } else {
            backgroundNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    hostKeyPrompts.firstOrNull()?.let { prompt ->
        val challenge = prompt.challenge
        val changed = challenge.kind == se.joynes.terminalhub.data.security.HostKeyChallengeKind.CHANGED
        val trusting = challenge in trustingHostKeys
        AlertDialog(
            onDismissRequest = { if (!trusting) viewModel.dismissHostKeyChallenge(prompt) },
            title = { Text(if (changed) "SSH HOST KEY CHANGED" else "TRUST SSH HOST?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (changed) {
                            "Connection blocked. Verify the server outside TerminalHub. Open Servers, edit this endpoint, and deliberately forget its trusted key before testing again."
                        } else {
                            "First contact. Compare this fingerprint with the SSH server before trusting it."
                        }
                    )
                    if (prompt.projectIds.size > 1) {
                        Text(
                            "${prompt.projectIds.size} tabs are waiting for this server. One approval reconnects them all.",
                            fontFamily = MonoFontFamily,
                            fontSize = 11.sp
                        )
                    }
                    challenge.trustedFingerprint?.let {
                        Text("Trusted: $it", fontFamily = MonoFontFamily, fontSize = 11.sp)
                    }
                    Text("Algorithm: ${challenge.presentedAlgorithm}", fontFamily = MonoFontFamily, fontSize = 11.sp)
                    Text("Presented: ${challenge.presentedFingerprint}", fontFamily = MonoFontFamily, fontSize = 11.sp)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !trusting,
                    onClick = {
                        if (changed) viewModel.dismissHostKeyChallenge(prompt)
                        else viewModel.trustHostKeyAndReconnect(prompt)
                    }
                ) { Text(if (trusting) "TRUSTING..." else if (changed) "OK" else "TRUST AND RECONNECT") }
            },
            dismissButton = if (changed) null else {
                {
                    TextButton(
                        enabled = !trusting,
                        onClick = { viewModel.dismissHostKeyChallenge(prompt) }
                    ) { Text("CANCEL") }
                }
            }
        )
    }

    if (showBackgroundSshRecommendation && hostKeyPrompts.isEmpty()) {
        AlertDialog(
            onDismissRequest = viewModel::dismissBackgroundSshRecommendation,
            title = { Text("KEEP SSH CONNECTED WHEN SWITCHING APPS?") },
            text = {
                Text(
                    "Recommended if you regularly switch to other apps. TerminalHub can keep active SSH " +
                        "connections alive with an ongoing notification, reducing reconnects when you return. " +
                        "Without it, switching away may mean reconnecting before you can continue. " +
                        "It may use battery and mobile data, and Android or the network can still interrupt it. " +
                        "tmux remains the reliable fallback. You can turn this off at any time in Settings."
                )
            },
            confirmButton = {
                TextButton(onClick = ::acceptBackgroundSshRecommendation) { Text("KEEP ALIVE") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissBackgroundSshRecommendation) { Text("NOT NOW") }
            }
        )
    }

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
    val failedConnectionTab = remember(projectTabs, runtimeState.recoveryActiveProjectId) {
        val recoveryId = runtimeState.recoveryActiveProjectId
        projectTabs.firstOrNull { it.projectId == recoveryId && it.connectionError != null }
            ?: projectTabs.firstOrNull { it.connectionError != null }
    }
    val connectingRemoteTabs = remember(projectTabs) {
        projectTabs.filter {
            it.targetType == se.joynes.terminalhub.data.model.ProjectTargetType.SSH && it.isConnecting
        }
    }
    val connectionOverlay = terminalConnectionOverlay(
        hasRenderedSession = session != null,
        hasConnectingRemoteTabs = connectingRemoteTabs.isNotEmpty(),
        activeRemoteTabDisconnected = activeTab != null &&
            activeTab.targetType == se.joynes.terminalhub.data.model.ProjectTargetType.SSH &&
            !activeTab.isConnected &&
            !activeTab.isConnecting &&
            activeTab.sessionId != null
    )
    val canReconnectActiveTab = activeTab != null &&
        activeTab.targetType == se.joynes.terminalhub.data.model.ProjectTargetType.SSH
    val activeTextInputVisible = activeProjectId?.let { textInputVisibleByProject[it] == true } ?: false
    val activeTextInputDraft = activeProjectId?.let { textInputDraftByProject[it] } ?: TextFieldValue()
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

    LaunchedEffect(activeProjectId, fileUploadState) {
        val projectId = activeProjectId ?: return@LaunchedEffect
        val completed = fileUploadState as? UploadState.Done ?: return@LaunchedEffect
        val draft = textInputDraftByProject[projectId] ?: TextFieldValue()
        textInputDraftByProject[projectId] = insertTextAtCursor(draft, completed.remotePath)
        fileUploadVisibleByProject[projectId] = false
        fileUploadSelectedUriByProject[projectId] = null
        fileUploadSelectedNameByProject[projectId] = ""
        fileUploadViewModel.reset(projectId)
        textInputVisibleByProject[projectId] = true
        Toast.makeText(context, "Uploaded path inserted in text input", Toast.LENGTH_SHORT).show()
    }

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

    fun sendTextInputToTerminal(text: String) {
        viewModel.sendTextInputToActive(text, executeTextInputOnSend)
    }

    fun syncRemotePty(tv: TerminalView, force: Boolean = false) {
        tv.setBackgroundColor(0xFF0D0D1A.toInt())
        tv.setCanvasBackgroundColor(0xFF0D0D1A.toInt())
        val viewChanged = tv.width != lastViewWidth || tv.height != lastViewHeight
        if (viewChanged || force) {
            lastViewWidth = tv.width
            lastViewHeight = tv.height
            tv.updateSize()
        }
        val emulator = tv.mEmulator ?: return
        val ptyChanged = emulator.mColumns != lastSyncedCols || emulator.mRows != lastSyncedRows
        if (ptyChanged || force) {
            lastSyncedCols = emulator.mColumns
            lastSyncedRows = emulator.mRows
            viewModel.resizeActivePty(emulator.mColumns, emulator.mRows)
        }
        if (viewChanged || ptyChanged || force) {
            tv.onScreenUpdated(true)
        }
    }

    fun requestTerminalResize(tv: TerminalView, force: Boolean = false) {
        tv.requestLayout()
        tv.invalidate()
        tv.post {
            syncRemotePty(tv, force = force)
            tv.onScreenUpdated(true)
        }
    }

    LaunchedEffect(activeId, session, imeBottomPx) {
        keyboardVisible = imeBottomPx > 0
        val tv = terminalViewRef.value ?: return@LaunchedEffect
        lastSyncedCols = -1
        lastSyncedRows = -1
        lastViewWidth = -1
        lastViewHeight = -1
        requestTerminalResize(tv, force = true)
        withFrameNanos { }
        terminalViewRef.value?.let { requestTerminalResize(it, force = true) }
        delay(120)
        terminalViewRef.value?.let { requestTerminalResize(it, force = true) }
        delay(220)
        terminalViewRef.value?.let { requestTerminalResize(it, force = true) }
        delay(500)
        terminalViewRef.value?.let { requestTerminalResize(it, force = true) }
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

    LaunchedEffect(pendingReconnectAll, projectTabs) {
        if (pendingReconnectAll && projectTabs.isNotEmpty()) {
            pendingReconnectAll = false
            viewModel.reconnectAllDisconnected()
        }
    }

    LaunchedEffect(requestedProjectId) {
        requestedProjectId?.let { viewModel.openProject(it) }
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
        TerminalHubAboutDialog(onDismiss = { showAboutDialog = false })
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
    pendingTmuxRestart?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingTmuxRestart = null },
            containerColor = MegaDriveSurface,
            title = {
                Text(
                    "RESTART TMUX?",
                    color = MegaDrivePrimary,
                    fontFamily = MonoFontFamily,
                    fontSize = 14.sp
                )
            },
            text = {
                Text(
                    "This stops the tmux session for ${pending.projectName}, including programs running inside it, then creates a fresh session in the same project tab.",
                    color = Color.White,
                    fontFamily = MonoFontFamily,
                    fontSize = 12.sp
                )
            },
            dismissButton = {
                RetroButton(
                    text = "CANCEL",
                    onClick = { pendingTmuxRestart = null }
                )
            },
            confirmButton = {
                RetroButton(
                    text = "RESTART",
                    onClick = {
                        viewModel.restartTmuxProject(pending.projectId)
                        pendingTmuxRestart = null
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
                        onSelect = {
                            viewModel.openProject(it)
                            terminalViewRef.value?.requestFocus()
                        },
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
                        onRestartTmux = { projectId ->
                            projectTabs.firstOrNull { it.projectId == projectId }?.let { tab ->
                                pendingTmuxRestart = PendingTmuxRestart(
                                    projectId = tab.projectId,
                                    projectName = tab.projectName
                                )
                            }
                        },
                        onReorder = viewModel::reorderSessions,
                        onAddProject = { onAddProject(serverId) },
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
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                    ) {
                        when {
                            BuildConfig.IS_DIAGNOSTIC -> {
                                Text(
                                    "NO LOCAL PROJECTS",
                                    color = MegaDriveDim,
                                    fontSize = 12.sp,
                                    fontFamily = MonoFontFamily
                                )
                                Spacer(Modifier.height(16.dp))
                                RetroButton("[ + ADD PROJECT ]", onAddServer)
                            }
                            homeState.hasServers -> {
                                Text(
                                    "SERVER READY",
                                    color = MegaDrivePrimary,
                                    fontSize = 12.sp,
                                    fontFamily = MonoFontFamily
                                )
                                Spacer(Modifier.height(8.dp))
                                homeState.selectedServer?.let { server ->
                                    Text(
                                        server.name,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontFamily = MonoFontFamily
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "${server.username}@${server.host}:${server.port}",
                                        color = MegaDriveOnSurface,
                                        fontSize = 11.sp,
                                        fontFamily = MonoFontFamily
                                    )
                                }
                                Spacer(Modifier.height(14.dp))
                                Text(
                                    "Create a project to open your first terminal session.",
                                    color = MegaDriveDim,
                                    fontSize = 11.sp,
                                    fontFamily = MonoFontFamily
                                )
                                Spacer(Modifier.height(16.dp))
                                RetroButton(
                                    "[ + ADD PROJECT ]",
                                    { onAddProject(homeState.selectedServer?.id) },
                                    Modifier.fillMaxWidth(),
                                    enabled = homeState.selectedServer != null
                                )
                                Spacer(Modifier.height(8.dp))
                                RetroButton("[ MANAGE SERVERS ]", onOpenServers, Modifier.fillMaxWidth())
                            }
                            else -> {
                                Text(
                                    "NO SERVER",
                                    color = MegaDriveOnSurface,
                                    fontSize = 12.sp,
                                    fontFamily = MonoFontFamily
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Start with a server, test SSH using a key, then create a project. The server screen includes a guide for starting SSH and finding its IP address.",
                                    color = MegaDriveOnSurface,
                                    fontSize = 11.sp,
                                    fontFamily = MonoFontFamily
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "1  ADD SERVER   →   2  TEST SSH   →   3  ADD PROJECT   →   4  ACTIVE NOTIFICATION   →   5  BATTERY: UNRESTRICTED",
                                    color = MegaDrivePrimary,
                                    fontSize = 10.sp,
                                    fontFamily = MonoFontFamily
                                )
                                Spacer(Modifier.height(16.dp))
                                RetroButton("[ + ADD SERVER ]", onAddServer, Modifier.fillMaxWidth())
                                Spacer(Modifier.height(8.dp))
                                RetroButton("[ SERVERS ]", onOpenServers, Modifier.fillMaxWidth())
                            }
                        }
                        if (!BuildConfig.IS_DIAGNOSTIC && homeState.projectCount > 0) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "${homeState.projectCount} saved project(s). Open Servers > Projects to reconnect closed sessions.",
                                color = MegaDriveDim,
                                fontSize = 10.sp,
                                fontFamily = MonoFontFamily
                            )
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
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = MegaDrivePrimary,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Text(
                                        if (runtimeState.recoveryPending) {
                                            "RESTORING ${restoringTab.projectName.uppercase()}…"
                                        } else {
                                            connectionProgressLabel(listOf(restoringTab.projectName))
                                        },
                                        color = MegaDrivePrimary,
                                        fontSize = 12.sp,
                                        fontFamily = MonoFontFamily
                                    )
                                }
                            }
                        } else if (failedConnectionTab != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MegaDriveBg)
                                    .padding(horizontal = 28.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        "CONNECTION FAILED",
                                        color = MegaDriveError,
                                        fontSize = 14.sp,
                                        fontFamily = MonoFontFamily
                                    )
                                    Text(
                                        failedConnectionTab.connectionError.orEmpty(),
                                        color = MegaDriveOnSurface,
                                        fontSize = 11.sp,
                                        fontFamily = MonoFontFamily,
                                        textAlign = TextAlign.Center
                                    )
                                    RetroButton(
                                        text = "RECONNECT",
                                        onClick = { viewModel.reconnectProject(failedConnectionTab.projectId) }
                                    )
                                }
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

                        if (connectionOverlay == TerminalConnectionOverlay.PROGRESS) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 12.dp)
                                    .background(MegaDriveSurface)
                                    .border(1.dp, MegaDrivePrimary.copy(alpha = 0.65f))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    color = MegaDrivePrimary,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    connectionProgressLabel(connectingRemoteTabs.map { it.projectName }),
                                    color = MegaDrivePrimary,
                                    fontSize = 12.sp,
                                    fontFamily = MonoFontFamily
                                )
                            }
                        }

                        if (connectionOverlay == TerminalConnectionOverlay.DISCONNECTED && activeTab != null) {
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
                            val textInputProjectId = activeProjectId
                            FloatingTextInputDialog(
                                text = activeTextInputDraft,
                                onTextChange = { updatedDraft ->
                                    val currentDraft = textInputDraftByProject[textInputProjectId]
                                        ?: TextFieldValue()
                                    textInputDraftByProject[textInputProjectId] = textInputDraftAfterChange(
                                        isInputVisible = textInputVisibleByProject[textInputProjectId] == true,
                                        currentDraft = currentDraft,
                                        updatedDraft = updatedDraft
                                    )
                                },
                                onSend = { text ->
                                    // Clear and close before handing the bytes to the terminal. Some IMEs emit
                                    // a final stale onValueChange after their Send action; the visibility guard
                                    // above prevents that callback from restoring the submitted command.
                                    textInputDraftByProject[textInputProjectId] = TextFieldValue()
                                    textInputVisibleByProject[textInputProjectId] = false
                                    sendTextInputToTerminal(text)
                                },
                                onDismiss = {
                                    textInputVisibleByProject[textInputProjectId] = false
                                    keyboardVisible = true
                                    showKeyboard()
                                },
                                history = textInputHistory,
                                onSaveHistory = { text ->
                                    viewModel.saveTextInput(textInputProjectId, text)
                                },
                                bottomAvoidanceDp = bottomBarReservedHeight,
                                panelOpacity = textInputPanelOpacity,
                                onPanelOpacityChange = { textInputPanelOpacity = it }
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
                            rows = keyBarRows,
                            onKey = { keyStr ->
                                if (activeTextInputVisible && activeProjectId != null) {
                                    when {
                                        keyStr == "\r" -> {
                                            val draft = textInputDraftByProject[activeProjectId] ?: TextFieldValue()
                                            if (draft.text.isNotEmpty()) {
                                                viewModel.saveTextInput(activeProjectId, draft.text)
                                                textInputDraftByProject[activeProjectId] = TextFieldValue()
                                                textInputVisibleByProject[activeProjectId] = false
                                                sendTextInputToTerminal(draft.text)
                                            }
                                        }
                                        (keyStr.length == 1 && keyStr[0] >= ' ' && keyStr[0] != '\u007F') || keyStr == "\t" -> {
                                            val draft = textInputDraftByProject[activeProjectId] ?: TextFieldValue()
                                            textInputDraftByProject[activeProjectId] = insertTextAtCursor(draft, keyStr)
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

internal fun textInputDraftAfterChange(
    isInputVisible: Boolean,
    currentDraft: TextFieldValue,
    updatedDraft: TextFieldValue
): TextFieldValue = if (isInputVisible) updatedDraft else currentDraft

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
