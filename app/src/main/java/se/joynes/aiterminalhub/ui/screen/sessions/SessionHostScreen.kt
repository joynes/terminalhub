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
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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

private val KeyBarReservedHeight = 104.dp


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
        tv.setBackgroundColor(0xFF0D0D1A.toInt())
        tv.setCanvasBackgroundColor(0xFF0D0D1A.toInt())
        tv.updateSize()
        tv.onScreenUpdated(true)
        tv.invalidate()
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .background(MegaDriveBg)
    ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(MegaDriveSurface)
                    .zIndex(1f),
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
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(MegaDriveBg)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = KeyBarReservedHeight)
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
                                        }
                                    )
                                }
                                LaunchedEffect(sess) {
                                    keyboardVisible = true
                                }
                                AndroidView(
                                    factory = { ctx ->
                                        val textSizePx = (14 * ctx.resources.displayMetrics.scaledDensity + 0.5f).toInt()
                                        TerminalView(ctx, null).apply {
                                            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                                            isFocusable = true
                                            isFocusableInTouchMode = true
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
                                        viewModel.debugLog("update: emu=${tv.mEmulator != null} w=${tv.width} h=${tv.height}")
                                        if (tv.mTermSession !== sess) {
                                            tv.attachSession(sess)
                                        }
                                        tv.setBackgroundColor(0xFF0D0D1A.toInt())
                                        tv.setCanvasBackgroundColor(0xFF0D0D1A.toInt())
                                        terminalViewRef.value = tv
                                        syncRemotePty(tv)
                                    },
                                    modifier = Modifier
                                        .fillMaxSize()
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

                        // Force Compose to use full compositing path for AndroidView
                        Spacer(modifier = Modifier.matchParentSize())

                        // DEBUG: diagnostic logging to app LogView
                        LaunchedEffect(Unit) {
                            while (true) {
                                kotlinx.coroutines.delay(1000)
                                val tv = terminalViewRef.value ?: continue
                                val sb = StringBuilder()
                                sb.append("emu=${tv.mEmulator != null} ${tv.width}x${tv.height}")
                                tv.mEmulator?.let { emu ->
                                    val palBg = emu.mColors.mCurrentColors[com.termux.terminal.TextStyle.COLOR_INDEX_BACKGROUND]
                                    sb.append(" palBg=#${Integer.toHexString(palBg)}")
                                }
                                try {
                                    @Suppress("DEPRECATION")
                                    tv.isDrawingCacheEnabled = true
                                    @Suppress("DEPRECATION")
                                    tv.drawingCache?.let { bmp ->
                                        val top = bmp.getPixel(bmp.width / 2, 10.coerceAtMost(bmp.height - 1))
                                        val mid = bmp.getPixel(bmp.width / 2, bmp.height / 2)
                                        val bot = bmp.getPixel(bmp.width / 2, (bmp.height * 9 / 10).coerceAtMost(bmp.height - 1))
                                        sb.append(" top=#${Integer.toHexString(top)} mid=#${Integer.toHexString(mid)} bot=#${Integer.toHexString(bot)}")
                                    }
                                    @Suppress("DEPRECATION")
                                    tv.isDrawingCacheEnabled = false
                                } catch (_: Exception) {}
                                sb.append(" alpha=${tv.alpha} layer=${tv.layerType}")
                                // Log cell styles at center position
                                tv.mEmulator?.let { emu ->
                                    val midRow = emu.mRows / 2
                                    val midCol = emu.mColumns / 2
                                    sb.append(" | cell: ${tv.debugCellAt(midRow, midCol)}")
                                    // Also check row 0 and last row
                                    sb.append(" | row0: ${tv.debugCellAt(0, 0)}")
                                    sb.append(" | lastRow: ${tv.debugCellAt(emu.mRows - 1, 0)}")
                                }
                                viewModel.debugLog(sb.toString())
                            }
                        }

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
                                },
                                bottomAvoidanceDp = KeyBarReservedHeight
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

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .fillMaxWidth()
                    ) {
                        SpecialKeyBar(
                            modifierManager = modifierManager,
                            onKey = {
                                viewModel.sendBytesToActive(it.toByteArray(Charsets.UTF_8))
                            },
                            onPaste = {
                                val text = clipboardManager.getText()?.text ?: return@SpecialKeyBar
                                viewModel.sendBytesToActive(text.toByteArray(Charsets.UTF_8))
                            },
                            onTextInput = {
                                showFileUpload = false
                                showTextInput = true
                            },
                            onFileUpload = {
                                showTextInput = false
                                showFileUpload = true
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
