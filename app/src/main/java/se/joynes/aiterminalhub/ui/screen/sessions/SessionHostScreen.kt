package se.joynes.aiterminalhub.ui.screen.sessions

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester

import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.clickable
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.hilt.navigation.compose.hiltViewModel
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.util.VelocityTracker

import org.connectbot.terminal.SelectionController
import org.connectbot.terminal.Terminal
import org.connectbot.terminal.TerminalEmulator
import se.joynes.aiterminalhub.ui.components.RetroButton
import se.joynes.aiterminalhub.ui.components.RetroTopBar
import se.joynes.aiterminalhub.ui.navigation.SessionTabBar
import se.joynes.aiterminalhub.ui.screen.terminal.MutableModifierManager
import se.joynes.aiterminalhub.ui.screen.terminal.SpecialKeyBar
import se.joynes.aiterminalhub.ui.theme.*

/**
 * Returns true once the emulator's scrollback buffer has at least one line.
 *
 * The Terminal composable's pointerInput gesture handler captures maxScroll at creation time.
 * Since pointerInput keys are (emulator, baseCharHeight) — NOT maxScroll — the handler is
 * stuck with maxScroll=0 until baseCharHeight changes (e.g. font resize).
 *
 * Fix: recreate the Terminal composable (via key()) once scrollback first appears. At that
 * point the new handler captures maxScroll = scrollback.size * charHeight > 0.
 */
@Composable
private fun rememberScrollbackExists(emulator: TerminalEmulator?): Boolean {
    var exists by remember(emulator) { mutableStateOf(false) }
    if (emulator != null && !exists) {
        LaunchedEffect(emulator) {
            Log.d("ScrollFix", "Starting scrollback watch")
            try {
                val method = emulator.javaClass.getMethod("getSnapshot\$lib")
                @Suppress("UNCHECKED_CAST")
                val flow = method.invoke(emulator) as? StateFlow<*>
                if (flow != null) {
                    Log.d("ScrollFix", "Watching snapshot flow for scrollback")
                    // Wait indefinitely for real scrollback — no timeout.
                    // In tmux (alt-screen) scrollback never appears in the emulator,
                    // so exists stays false and the PageUp/PageDown handler stays active.
                    // In a plain shell, scrollback builds up quickly and triggers this.
                    flow.first { snapshot ->
                        val scrollback = runCatching {
                            snapshot!!.javaClass.getMethod("getScrollback").invoke(snapshot)
                        }.getOrNull()
                        val size = (scrollback as? Collection<*>)?.size ?: 0
                        Log.d("ScrollFix", "snapshot scrollback.size=$size")
                        size > 0
                    }
                    Log.d("ScrollFix", "exists = true → Terminal will recreate")
                    exists = true
                }
                // If flow is null, leave exists=false — keeps PageUp/PageDown active.
            } catch (e: Exception) {
                Log.e("ScrollFix", "Reflection failed: $e — leaving exists=false for tmux compat")
                // Do NOT set exists=true — keep PageUp/PageDown active indefinitely.
            }
        }
    }
    return exists
}

@Composable
fun SessionHostScreen(
    onEditServer: () -> Unit,
    onAddProject: () -> Unit,
    onOpenLogs: () -> Unit = {},
    viewModel: SessionHostViewModel = hiltViewModel()
) {
    val projectTabs by viewModel.projectTabs.collectAsState()
    val sessions by viewModel.sessionManager.sessions.collectAsState()
    val activeId by viewModel.activeId.collectAsState()
    val emulator by viewModel.activeEmulator.collectAsState()
    val serverId by viewModel.serverId.collectAsState()
    val closedSessions by viewModel.sessionManager.closedSessions.collectAsState()
    val clipboardManager = LocalClipboardManager.current

    val focusRequester = remember { FocusRequester() }
    var keyboardVisible by remember { mutableStateOf(true) }
    var showSessionHistory by remember { mutableStateOf(false) }

    // Shared modifier manager: toggles in SpecialKeyBar are read by Terminal's KeyboardHandler
    val modifierManager = remember { MutableModifierManager() }

    LaunchedEffect(Unit) { viewModel.init() }

    // Re-request focus when the app comes back from background (Bug: keyboard dead after re-enter)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && emulator != null) {
                focusRequester.requestFocus()
                keyboardVisible = true
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
            onSelect = { viewModel.switchToSession(it); focusRequester.requestFocus() },
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
                    "✦",
                    color = MegaDriveDim,
                    fontSize = 14.sp,
                    fontFamily = MonoFontFamily,
                    modifier = androidx.compose.ui.Modifier
                        .padding(end = 12.dp)
                        .clickable { onOpenLogs() }
                )
                if (serverId != null) {
                    Text(
                        "✎",
                        color = MegaDriveDim,
                        fontSize = 16.sp,
                        fontFamily = MonoFontFamily,
                        modifier = androidx.compose.ui.Modifier
                            .padding(end = 12.dp)
                            .clickable { onEditServer() }
                    )
                }
                if (sessions.isNotEmpty()) {
                    Text(
                        "≡",
                        color = MegaDrivePrimary,
                        fontSize = 20.sp,
                        fontFamily = MonoFontFamily,
                        modifier = androidx.compose.ui.Modifier
                            .padding(end = 8.dp)
                            .clickable { showSessionHistory = true }
                    )
                }
            })
        },
        containerColor = MegaDriveBg
    ) { padding ->
        val density = LocalDensity.current
        val imeBottom = WindowInsets.ime.getBottom(density)

        // Use the live IME inset directly — no saved-height tricks.
        // When keyboardVisible is false we want 0 padding regardless.
        // WindowInsets.ime animates smoothly in Compose so this is glitch-free.
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
                // No projects configured at all
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
                    onSelect = { viewModel.switchToSession(it); focusRequester.requestFocus() },
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
                    val em = emulator
                    if (em != null) {
                        // key(em) forces Terminal to fully recreate on tab switch, so its
                        // ImeInputView is always wired to the current emulator's onKeyboardInput.
                        // Without this, the old IME connection persists → input goes to the
                        // wrong session and duplicate characters appear.
                        //
                        // scrollbackExists: the Terminal's pointerInput gesture handler captures
                        // maxScroll at creation time. When first created, scrollback is empty →
                        // maxScroll=0 → scroll is broken. Adding scrollbackExists as a key forces
                        // one extra recreation once scrollback appears, capturing maxScroll > 0.
                        val scrollbackExists = rememberScrollbackExists(em)
                        var selectionController by remember(em) { mutableStateOf<SelectionController?>(null) }
                        // In alt-screen mode (tmux), intercept vertical swipes and translate
                        // them to tmux scroll commands using the Initial pointer pass.
                        // Direction (natural mobile): finger DOWN = older history, finger UP = newer.
                        // First down-scroll: Ctrl+B + PageUp (\x02\033[5~) = "prefix PPage" =
                        //   copy-mode -u (enters copy mode + scrolls up one page).
                        // Subsequent down-scrolls: bare PageUp (\033[5~) — already in copy mode.
                        // Up-scrolls: PageDown (\033[6~) — scrolls toward current output.
                        // Speed: one page per 150dp of drag, fires continuously during the gesture.
                        // When scrollbackExists=true (normal screen) the modifier is a no-op.
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(scrollbackExists) {
                                    if (scrollbackExists) return@pointerInput
                                    val scrollUnit = 80.dp.toPx()   // ~3.4 cm, clearly intentional
                                    var flingJob: Job? = null
                                    kotlinx.coroutines.coroutineScope {
                                    val gestureScope = this
                                    awaitEachGesture {
                                        flingJob?.cancel()
                                        flingJob = null
                                        // Reset per gesture: if user pressed q to exit copy mode
                                        // between gestures, we correctly re-send the tmux prefix.
                                        var inCopyMode = false
                                        val velocityTracker = VelocityTracker()
                                        var lastY = 0f
                                        var accumulated = 0f
                                        val down = awaitPointerEvent(PointerEventPass.Initial)
                                        down.changes.firstOrNull()?.also { c ->
                                            lastY = c.position.y
                                            velocityTracker.addPosition(c.uptimeMillis, c.position)
                                        }
                                        while (true) {
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                            val change = event.changes.firstOrNull() ?: break
                                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                                            val delta = change.position.y - lastY
                                            lastY = change.position.y
                                            accumulated += delta
                                            // Consume all move events — prevents terminal selection drag
                                            change.consume()
                                            // Clear any accidental long-press selection the Terminal may have started
                                            if (abs(accumulated) > 2.dp.toPx()) {
                                                selectionController?.clearSelection()
                                            }
                                            // Finger DOWN → see older history (scroll up in copy mode)
                                            while (accumulated >= scrollUnit) {
                                                accumulated -= scrollUnit
                                                val bytes = if (!inCopyMode) {
                                                    inCopyMode = true
                                                    viewModel.notifyEnteredCopyMode()
                                                    "\u0002\u001B[5~".toByteArray() // prefix+PageUp = copy-mode -u
                                                } else {
                                                    "\u001B[5~".toByteArray()        // PageUp (already in copy mode)
                                                }
                                                viewModel.sendBytesToActive(bytes)
                                                Log.d("ScrollFix", "Scroll older")
                                            }
                                            // Finger UP → see newer content (scroll down)
                                            while (accumulated <= -scrollUnit) {
                                                accumulated += scrollUnit
                                                viewModel.sendBytesToActive("\u001B[6~".toByteArray()) // PageDown
                                                Log.d("ScrollFix", "Scroll newer")
                                            }
                                            if (!change.pressed) {
                                                val vy = velocityTracker.calculateVelocity().y
                                                // flingPages: scale velocity → extra pages, cap at 12
                                                val flingPages = (abs(vy) / scrollUnit * 0.15f)
                                                    .toInt().coerceIn(0, 12)
                                                if (flingPages > 0) {
                                                    val scrollOlder = vy > 0 // finger was moving down
                                                    val wasInCopyMode = inCopyMode
                                                    if (!wasInCopyMode) {
                                                        inCopyMode = true
                                                        viewModel.notifyEnteredCopyMode()
                                                    }
                                                    flingJob = gestureScope.launch {
                                                        repeat(flingPages) { i ->
                                                            val bytes = if (scrollOlder) {
                                                                if (i == 0 && !wasInCopyMode)
                                                                    "\u0002\u001B[5~".toByteArray()
                                                                else
                                                                    "\u001B[5~".toByteArray()
                                                            } else {
                                                                "\u001B[6~".toByteArray()
                                                            }
                                                            viewModel.sendBytesToActive(bytes)
                                                            delay(30L + i * 20L) // decelerate
                                                        }
                                                    }
                                                }
                                                // Restore keyboard focus after gesture
                                                // (consuming touch events can cause ImeInputView to lose focus)
                                                focusRequester.requestFocus()
                                                break
                                            }
                                        }
                                    }
                                    } // coroutineScope
                                }
                        ) {
                            key(em, scrollbackExists) {
                                // Request focus INSIDE key() so it fires after the new ImeInputView
                                // has attached to focusRequester. The outer LaunchedEffect(emulator)
                                // fires too early — before the recreated Terminal has laid out,
                                // causing the focus request to land in empty space.
                                LaunchedEffect(em) {
                                    focusRequester.requestFocus()
                                    keyboardVisible = true
                                }
                                SideEffect { Log.d("ScrollFix", "Terminal composed. scrollbackExists=$scrollbackExists") }
                                Terminal(
                                    terminalEmulator = em,
                                    modifier = Modifier.fillMaxSize(),
                                    keyboardEnabled = true,
                                    showSoftKeyboard = keyboardVisible,
                                    initialFontSize = 12.sp,
                                    focusRequester = focusRequester,
                                    modifierManager = modifierManager,
                                    onSelectionControllerAvailable = { selectionController = it },
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

                    // Floating text input overlay — lives inside the terminal Box so it
                    // shares the same window as the IME (required for voice/dictation to work).
                    if (showTextInput) {
                        FloatingTextInputDialog(
                            onSend = { text ->
                                viewModel.sendBytesToActive(text.toByteArray(Charsets.UTF_8))
                            },
                            onDismiss = {
                                showTextInput = false
                                focusRequester.requestFocus()
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
                        if (keyboardVisible) focusRequester.requestFocus()
                    },
                    onPrevTab = {
                        val connected = projectTabs.filter { it.sessionId != null }
                        val curIdx = connected.indexOfFirst { it.sessionId == activeId }
                        if (curIdx > 0) {
                            connected[curIdx - 1].sessionId?.let {
                                viewModel.switchToSession(it)
                                focusRequester.requestFocus()
                            }
                        }
                    },
                    onNextTab = {
                        val connected = projectTabs.filter { it.sessionId != null }
                        val curIdx = connected.indexOfFirst { it.sessionId == activeId }
                        if (curIdx in 0 until connected.size - 1) {
                            connected[curIdx + 1].sessionId?.let {
                                viewModel.switchToSession(it)
                                focusRequester.requestFocus()
                            }
                        }
                    }
                )
            }
        }
    }
}
