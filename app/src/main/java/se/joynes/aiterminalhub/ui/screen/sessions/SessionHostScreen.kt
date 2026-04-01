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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.clickable
import androidx.hilt.navigation.compose.hiltViewModel
import se.joynes.aiterminalhub.R
import se.joynes.aiterminalhub.BuildConfig
import se.joynes.aiterminalhub.ui.screen.export.ExportImportState
import se.joynes.aiterminalhub.ui.screen.export.ExportImportViewModel
import se.joynes.aiterminalhub.ui.screen.upload.FileUploadViewModel
import se.joynes.aiterminalhub.ui.screen.upload.FloatingFileUploadDialog
import se.joynes.aiterminalhub.ui.screen.upload.UploadState
import com.termux.view.TerminalView
import se.joynes.aiterminalhub.ui.components.RetroButton
import se.joynes.aiterminalhub.ui.navigation.SessionTabBar
import se.joynes.aiterminalhub.ui.screen.terminal.MutableModifierManager
import se.joynes.aiterminalhub.ui.screen.terminal.SpecialKeyBar
import se.joynes.aiterminalhub.ui.screen.terminal.TerminalViewClientImpl
import se.joynes.aiterminalhub.ui.theme.*

private val KeyBarReservedHeight = 70.dp

private data class PendingTabClose(
    val projectId: Long,
    val projectName: String,
    val sessionId: se.joynes.aiterminalhub.domain.TerminalSessionId?
)

private fun shouldUseSoftwareTerminalLayer(): Boolean {
    return Build.MANUFACTURER.equals("samsung", ignoreCase = true) ||
        Build.BRAND.equals("samsung", ignoreCase = true)
}


@Composable
fun SessionHostScreen(
    onOpenServers: () -> Unit,
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
    val activeTextInputVisible = activeProjectId?.let { textInputVisibleByProject[it] == true } ?: false
    val activeTextInputDraft = activeProjectId?.let { textInputDraftByProject[it].orEmpty() }.orEmpty()
    val activeFileUploadVisible = activeProjectId?.let { fileUploadVisibleByProject[it] == true } ?: false
    val activeFileUploadSelectedUri = activeProjectId?.let { fileUploadSelectedUriByProject[it] }
    val activeFileUploadSelectedName = activeProjectId?.let { fileUploadSelectedNameByProject[it].orEmpty() }.orEmpty()
    val textInputHistory by remember(activeProjectId) {
        activeProjectId?.let { viewModel.textInputHistory(it) } ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())
    val fileUploadState by remember(activeProjectId) {
        activeProjectId?.let { fileUploadViewModel.uploadState(it) } ?: kotlinx.coroutines.flow.flowOf(UploadState.Idle)
    }.collectAsState(initial = UploadState.Idle)

    // Shared modifier manager: toggles in SpecialKeyBar are read by TerminalViewClientImpl
    val modifierManager = remember { MutableModifierManager() }

    // Reference to the live TerminalView for direct IMM calls
    val terminalViewRef = remember { mutableStateOf<TerminalView?>(null) }
    val blackSwatchRef = remember { mutableStateOf<android.view.View?>(null) }
    val canvasProbeRef = remember { mutableStateOf<android.view.View?>(null) }

    fun applyTerminalRenderCompatibility(tv: TerminalView) {
        if (shouldUseSoftwareTerminalLayer()) {
            tv.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
        } else {
            tv.setLayerType(android.view.View.LAYER_TYPE_NONE, null)
        }
    }

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
        applyTerminalRenderCompatibility(tv)
        tv.setBackgroundColor(0xFF0D0D1A.toInt())
        tv.setCanvasBackgroundColor(0xFF0D0D1A.toInt())
        tv.updateSize()
        tv.onScreenUpdated(true)
        tv.invalidate()
        val emulator = tv.mEmulator ?: return
        viewModel.resizeActivePty(emulator.mColumns, emulator.mRows)
    }

    LaunchedEffect(sharedUri) {
        val projectId = activeProjectId
        if (sharedUri != null && projectId != null) {
            fileUploadSelectedUriByProject[projectId] = sharedUri
            textInputVisibleByProject[projectId] = false
            fileUploadVisibleByProject[projectId] = true
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
                            "Delete project from app",
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
                                            applyTerminalRenderCompatibility(this)
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
                                        viewModel.debugLog("update: emu=${tv.mEmulator != null} w=${tv.width} h=${tv.height}")
                                        if (tv.mTermSession !== sess) {
                                            tv.attachSession(sess)
                                        }
                                        applyTerminalRenderCompatibility(tv)
                                        tv.setBackgroundColor(0xFF0D0D1A.toInt())
                                        tv.setCanvasBackgroundColor(0xFF0D0D1A.toInt())
                                        terminalViewRef.value = tv
                                        syncRemotePty(tv)
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            if (BuildConfig.IS_DIAGNOSTIC) {
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    AndroidView(
                                        factory = { ctx ->
                                            android.view.View(ctx).apply {
                                                setBackgroundColor(android.graphics.Color.BLACK)
                                            }.also { blackSwatchRef.value = it }
                                        },
                                        update = { swatch ->
                                            swatch.setBackgroundColor(android.graphics.Color.BLACK)
                                            blackSwatchRef.value = swatch
                                        },
                                        modifier = Modifier.size(28.dp)
                                    )
                                    AndroidView(
                                        factory = { ctx ->
                                            object : android.view.View(ctx) {
                                                private val paint = android.graphics.Paint().apply {
                                                    color = android.graphics.Color.BLACK
                                                    style = android.graphics.Paint.Style.FILL
                                                }

                                                override fun onDraw(canvas: android.graphics.Canvas) {
                                                    canvas.drawRect(
                                                        0f,
                                                        0f,
                                                        width.toFloat(),
                                                        height.toFloat(),
                                                        paint
                                                    )
                                                }
                                            }.also { canvasProbeRef.value = it }
                                        },
                                        update = { probe ->
                                            canvasProbeRef.value = probe
                                            probe.invalidate()
                                        },
                                        modifier = Modifier.size(28.dp)
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
                                    "CONNECTING...",
                                    color = MegaDrivePrimary,
                                    fontSize = 12.sp,
                                    fontFamily = MonoFontFamily
                                )
                            }
                        }

                        // DEBUG: diagnostic logging to app LogView
                        if (BuildConfig.IS_DIAGNOSTIC) LaunchedEffect(Unit) {
                            while (true) {
                                kotlinx.coroutines.delay(1000)
                                val tv = terminalViewRef.value ?: continue
                                val sb = StringBuilder()
                                sb.append("emu=${tv.mEmulator != null} ${tv.width}x${tv.height}")
                                tv.mEmulator?.let { emu ->
                                    val palette = emu.mColors.mCurrentColors
                                    val palBg = palette[com.termux.terminal.TextStyle.COLOR_INDEX_BACKGROUND]
                                    val palFg = palette[com.termux.terminal.TextStyle.COLOR_INDEX_FOREGROUND]
                                    sb.append(" palBg=#${Integer.toHexString(palBg)}")
                                    sb.append(" palFg=#${Integer.toHexString(palFg)}")
                                    sb.append(" reverseVideo=${emu.isReverseVideo}")
                                    // Log first 8 palette entries in case shell changed them
                                    sb.append(" pal0-7=[${(0..7).joinToString(",") { "#${Integer.toHexString(palette[it])}" }}]")
                                }
                                try {
                                    @Suppress("DEPRECATION")
                                    tv.isDrawingCacheEnabled = true
                                    @Suppress("DEPRECATION")
                                    tv.drawingCache?.let { bmp ->
                                        val safeX = 4.coerceAtMost(bmp.width - 1)
                                        val safeY = 4.coerceAtMost(bmp.height - 1)
                                        val tl = bmp.getPixel(safeX, safeY)
                                        val tr = bmp.getPixel((bmp.width - 5).coerceAtLeast(0), safeY)
                                        val bl = bmp.getPixel(safeX, (bmp.height - 5).coerceAtLeast(0))
                                        val br = bmp.getPixel((bmp.width - 5).coerceAtLeast(0), (bmp.height - 5).coerceAtLeast(0))
                                        val mid = bmp.getPixel(bmp.width / 2, bmp.height / 2)
                                        sb.append(
                                            " px tl=#${Integer.toHexString(tl)} tr=#${Integer.toHexString(tr)}" +
                                                " bl=#${Integer.toHexString(bl)} br=#${Integer.toHexString(br)}" +
                                                " mid=#${Integer.toHexString(mid)}"
                                        )
                                    }
                                    @Suppress("DEPRECATION")
                                    tv.isDrawingCacheEnabled = false
                                } catch (_: Exception) {}
                                try {
                                    val swatch = blackSwatchRef.value
                                    @Suppress("DEPRECATION")
                                    swatch?.isDrawingCacheEnabled = true
                                    @Suppress("DEPRECATION")
                                    swatch?.drawingCache?.let { bmp ->
                                        val sample = bmp.getPixel(
                                            (bmp.width / 2).coerceAtMost(bmp.width - 1),
                                            (bmp.height / 2).coerceAtMost(bmp.height - 1)
                                        )
                                        sb.append(" swatch=#${Integer.toHexString(sample)}")
                                    }
                                    @Suppress("DEPRECATION")
                                    swatch?.isDrawingCacheEnabled = false
                                } catch (_: Exception) {}
                                try {
                                    val probe = canvasProbeRef.value
                                    @Suppress("DEPRECATION")
                                    probe?.isDrawingCacheEnabled = true
                                    @Suppress("DEPRECATION")
                                    probe?.drawingCache?.let { bmp ->
                                        val sample = bmp.getPixel(
                                            (bmp.width / 2).coerceAtMost(bmp.width - 1),
                                            (bmp.height / 2).coerceAtMost(bmp.height - 1)
                                        )
                                        sb.append(" probe=#${Integer.toHexString(sample)}")
                                    }
                                    @Suppress("DEPRECATION")
                                    probe?.isDrawingCacheEnabled = false
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
                                activeProjectId?.let { projectId ->
                                    fileUploadVisibleByProject[projectId] = false
                                    textInputVisibleByProject[projectId] = true
                                }
                            },
                            onFileUpload = {
                                activeProjectId?.let { projectId ->
                                    textInputVisibleByProject[projectId] = false
                                    fileUploadVisibleByProject[projectId] = true
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
