package se.joynes.aiterminalhub.ui.screen.sessions

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
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.clickable
import androidx.hilt.navigation.compose.hiltViewModel
import se.joynes.aiterminalhub.ui.screen.export.ExportImportState
import se.joynes.aiterminalhub.ui.screen.export.ExportImportViewModel
import se.joynes.aiterminalhub.ui.screen.upload.FileUploadViewModel
import se.joynes.aiterminalhub.ui.screen.upload.FloatingFileUploadDialog
import com.termux.view.TerminalView
import se.joynes.aiterminalhub.ui.components.RetroButton
import se.joynes.aiterminalhub.ui.navigation.SessionTabBar
import se.joynes.aiterminalhub.ui.screen.terminal.MutableModifierManager
import se.joynes.aiterminalhub.ui.screen.terminal.SpecialKeyBar
import se.joynes.aiterminalhub.ui.screen.terminal.TerminalViewClientImpl
import se.joynes.aiterminalhub.ui.theme.*


@Composable
fun SessionHostScreen(
    onEditServer: () -> Unit,
    onAddServer: () -> Unit,
    onAddProject: () -> Unit,
    onOpenLogs: () -> Unit,
    sharedUri: Uri? = null,
    onConsumeSharedUri: () -> Unit = {},
    viewModel: SessionHostViewModel = hiltViewModel()
) {
    val projectTabs by viewModel.projectTabs.collectAsState()
    val sessions by viewModel.sessionManager.sessions.collectAsState()
    val activeId by viewModel.activeId.collectAsState()
    val session by viewModel.activeSession.collectAsState()
    val serverId by viewModel.serverId.collectAsState()
    val closedSessions by viewModel.sessionManager.closedSessions.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    var keyboardVisible by remember { mutableStateOf(true) }
    var showSessionHistory by remember { mutableStateOf(false) }
    var showSettingsMenu by remember { mutableStateOf(false) }
    var showTextInput by remember { mutableStateOf(false) }
    var showFileUpload by remember { mutableStateOf(false) }
    val fileUploadViewModel: FileUploadViewModel = hiltViewModel()
    val exportImportViewModel: ExportImportViewModel = hiltViewModel()
    val exportImportState by exportImportViewModel.state.collectAsState()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri -> uri?.let { exportImportViewModel.export(context, it) } }

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
    val textInputHistory by remember(activeProjectId) {
        activeProjectId?.let { viewModel.textInputHistory(it) } ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    // Shared modifier manager: toggles in SpecialKeyBar are read by TerminalViewClientImpl
    val modifierManager = remember { MutableModifierManager() }

    // Reference to the live TerminalView for direct IMM calls
    val terminalViewRef = remember { mutableStateOf<TerminalView?>(null) }

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
        tv.updateSize()
        val emulator = tv.mEmulator ?: return
        viewModel.resizeActivePty(emulator.mColumns, emulator.mRows)
    }

    // Auto-open upload dialog when a file is shared to this app
    var pendingSharedUri by remember { mutableStateOf<Uri?>(null) }
    LaunchedEffect(sharedUri) {
        if (sharedUri != null) {
            pendingSharedUri = sharedUri
            showTextInput = false
            showFileUpload = true
            onConsumeSharedUri()
        }
    }

    LaunchedEffect(Unit) { viewModel.init() }

    LaunchedEffect(viewModel) {
        viewModel.screenUpdates.collect { changedSession ->
            val tv = terminalViewRef.value ?: return@collect
            if (tv.mTermSession === changedSession) {
                tv.onScreenUpdated()
            }
        }
    }

    // Re-request focus when the app comes back from background
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentSession by rememberUpdatedState(session)
    val currentActiveId by rememberUpdatedState(activeId)
    val currentViewModel by rememberUpdatedState(viewModel)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val tv = terminalViewRef.value
            if (event == Lifecycle.Event.ON_RESUME && currentSession != null) {
                keyboardVisible = true
                tv?.requestFocus()
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

    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)

    val imeBottomDp = with(density) {
        if (keyboardVisible && imeBottom > 0) imeBottom.toDp() else 0.dp
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(bottom = imeBottomDp)
            .background(MegaDriveBg)
    ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(MegaDriveSurface),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (projectTabs.isNotEmpty()) {
                    SessionTabBar(
                        tabs = projectTabs,
                        activeId = activeId,
                        onSelect = { viewModel.switchToSession(it); terminalViewRef.value?.requestFocus() },
                        onClose = { projectId, sessionId -> viewModel.closeSession(projectId, sessionId) },
                        onMove = { fromIndex, toIndex -> viewModel.moveSession(fromIndex, toIndex) },
                        onAddProject = onAddProject,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
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
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Edit Server",
                                    color = if (serverId != null) Color.White else MegaDriveDim,
                                    fontFamily = MonoFontFamily,
                                    fontSize = 12.sp
                                )
                            },
                            onClick = {
                                showSettingsMenu = false
                                if (serverId != null) onEditServer()
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
                                exportLauncher.launch("aiterminalhub_backup.yaml")
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
                    }
                }
            }

            if (projectTabs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (serverId == null) {
                            Text(
                                "NO SERVER",
                                color = MegaDriveDim,
                                fontSize = 12.sp,
                                fontFamily = MonoFontFamily
                            )
                            Spacer(Modifier.height(16.dp))
                            RetroButton("[ + ADD SERVER ]", onAddServer)
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
                // Single active terminal pane
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(MegaDriveBg)
                ) {
                    val sess = session
                    if (sess != null) {
                        // key(sess) forces TerminalView to fully recreate on tab switch
                        key(sess) {
                            val terminalViewClient = remember(sess) {
                                TerminalViewClientImpl(
                                    modifierManager = modifierManager,
                                    onSendToSsh = { bytes -> viewModel.sendBytesToActive(bytes) },
                                    onTerminalTap = {
                                        keyboardVisible = true
                                        showKeyboard()
                                    }
                                )
                            }
                            LaunchedEffect(sess) {
                                keyboardVisible = true
                                // Keyboard is shown by view.post{} inside the factory;
                                // just ensure the state flag is set here.
                            }
                            AndroidView(
                                factory = { ctx ->
                                    val textSizePx = (14 * ctx.resources.displayMetrics.scaledDensity + 0.5f).toInt()
                                    TerminalView(ctx, null).apply {
                                        isFocusable = true
                                        isFocusableInTouchMode = true
                                        setBackgroundColor(0xFF0D0D1A.toInt())
                                        setTextSize(textSizePx)
                                        setTerminalViewClient(terminalViewClient)
                                        attachSession(sess)
                                        addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                                            syncRemotePty(view as TerminalView)
                                        }
                                    }.also { tv ->
                                        terminalViewRef.value = tv
                                        // post{} runs after the view is attached to the window,
                                        // which is required for showSoftInput() to succeed.
                                        tv.post {
                                            syncRemotePty(tv)
                                            tv.requestFocusFromTouch()
                                            tv.requestFocus()
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                                tv.windowInsetsController?.show(AndroidWindowInsets.Type.ime())
                                            } else {
                                                ctx.getSystemService(InputMethodManager::class.java)
                                                    ?.showSoftInput(tv, InputMethodManager.SHOW_FORCED)
                                            }
                                        }
                                    }
                                },
                                update = { tv ->
                                    if (tv.mTermSession !== sess) {
                                        tv.attachSession(sess)
                                    }
                                    terminalViewRef.value = tv
                                    syncRemotePty(tv)
                                },
                                modifier = Modifier.fillMaxSize()
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
                                "CONNECTING...",
                                color = MegaDrivePrimary,
                                fontSize = 12.sp,
                                fontFamily = MonoFontFamily
                            )
                        }
                    }

                    // Floating text input overlay
                    if (showTextInput) {
                        FloatingTextInputDialog(
                            onSend = { text ->
                                val payload = if (text.endsWith("\n") || text.endsWith("\r")) text else "$text\r"
                                viewModel.sendBytesToActive(payload.toByteArray(Charsets.UTF_8))
                            },
                            onDismiss = {
                                showTextInput = false
                                keyboardVisible = true
                                showKeyboard()
                            },
                            history = textInputHistory,
                            onSaveHistory = { text ->
                                activeProjectId?.let { viewModel.saveTextInput(it, text) }
                            }
                        )
                    }

                    if (showFileUpload) {
                        FloatingFileUploadDialog(
                            viewModel = fileUploadViewModel,
                            projectId = activeProjectId ?: 0L,
                            serverId = serverId ?: 0L,
                            initialUri = pendingSharedUri,
                            onDismiss = {
                                showFileUpload = false
                                pendingSharedUri = null
                                terminalViewRef.value?.requestFocus()
                            }
                        )
                    }
                }

                SpecialKeyBar(
                    modifierManager = modifierManager,
                    onKey = {
                        viewModel.sendBytesToActive(it.toByteArray(Charsets.UTF_8))
                    },
                    onPaste = {
                        val text = clipboardManager.getText()?.text ?: return@SpecialKeyBar
                        viewModel.sendBytesToActive(text.toByteArray(Charsets.UTF_8))
                    },
                    onTextInput = { showFileUpload = false; showTextInput = true },
                    onFileUpload = { showTextInput = false; showFileUpload = true },
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

