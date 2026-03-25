package se.joynes.aiterminalhub.ui.screen.sessions

import android.os.Build
import android.view.WindowInsets as AndroidWindowInsets
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.foundation.clickable
import androidx.hilt.navigation.compose.hiltViewModel
import com.termux.view.TerminalView
import se.joynes.aiterminalhub.data.logging.AppLogger
import se.joynes.aiterminalhub.data.logging.LogEvent
import se.joynes.aiterminalhub.data.logging.LogLevel
import se.joynes.aiterminalhub.ui.components.RetroButton
import se.joynes.aiterminalhub.ui.components.RetroTopBar
import se.joynes.aiterminalhub.ui.navigation.SessionTabBar
import se.joynes.aiterminalhub.ui.screen.terminal.MutableModifierManager
import se.joynes.aiterminalhub.ui.screen.terminal.SpecialKeyBar
import se.joynes.aiterminalhub.ui.screen.terminal.TerminalViewClientImpl
import se.joynes.aiterminalhub.ui.theme.*


@Composable
fun SessionHostScreen(
    onEditServer: () -> Unit,
    onAddProject: () -> Unit,
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
    val logger = remember { dagger.hilt.android.EntryPointAccessors.fromApplication(
        context.applicationContext,
        SessionHostScreenLoggerEntryPoint::class.java
    ).appLogger() }

    var keyboardVisible by remember { mutableStateOf(true) }
    var showSessionHistory by remember { mutableStateOf(false) }

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
            logger.log(
                LogLevel.INFO,
                "SessionHostScreen",
                "Lifecycle event: $event screen=${System.identityHashCode(lifecycleOwner)} activeId=${currentActiveId?.value} terminalView=${tv?.let { System.identityHashCode(it) }} terminalSession=${currentSession?.let { System.identityHashCode(it) }} vm={${currentViewModel.debugSnapshot()}}",
                LogEvent.AppEvent("session_host_$event")
            )
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
            onClose = { id ->
                val tab = projectTabs.firstOrNull { it.sessionId == id } ?: return@SessionHistorySheet
                viewModel.closeSession(tab.projectId, id)
            },
            onReopen = { projectId -> viewModel.reopenSession(projectId) },
            onMoveUp = { idx -> viewModel.moveSession(idx, idx - 1) },
            onMoveDown = { idx -> viewModel.moveSession(idx, idx + 1) },
            onDismiss = { showSessionHistory = false }
        )
    }

    Scaffold(
        topBar = {
            RetroTopBar(title = "TERMINAL", onBack = null, actions = {
                Text(
                    "SET",
                    color = if (serverId != null) MegaDrivePrimary else MegaDriveDim,
                    fontSize = 10.sp,
                    fontFamily = MonoFontFamily,
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .clickable(enabled = serverId != null) { onEditServer() }
                )
            })
        },
        containerColor = MegaDriveBg
    ) { padding ->
        val density = LocalDensity.current
        val imeBottom = WindowInsets.ime.getBottom(density)

        val imeBottomDp = with(density) {
            if (keyboardVisible && imeBottom > 0) imeBottom.toDp() else 0.dp
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(bottom = imeBottomDp)
                .background(MegaDriveBg)
        ) {
            if (projectTabs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
            } else {
                SessionTabBar(
                    tabs = projectTabs,
                    activeId = activeId,
                    onSelect = { viewModel.switchToSession(it); terminalViewRef.value?.requestFocus() },
                    onClose = { projectId, sessionId -> viewModel.closeSession(projectId, sessionId) },
                    onAddProject = onAddProject
                )

                var showTextInput by remember { mutableStateOf(false) }

                // Single active terminal pane
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
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
                                viewModel.sendBytesToActive(text.toByteArray(Charsets.UTF_8))
                            },
                            onDismiss = {
                                showTextInput = false
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
                    onTextInput = { showTextInput = true },
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

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface SessionHostScreenLoggerEntryPoint {
    fun appLogger(): AppLogger
}
