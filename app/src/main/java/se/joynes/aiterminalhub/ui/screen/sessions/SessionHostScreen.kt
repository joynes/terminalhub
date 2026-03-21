package se.joynes.aiterminalhub.ui.screen.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import org.connectbot.terminal.Terminal
import se.joynes.aiterminalhub.ui.components.RetroButton
import se.joynes.aiterminalhub.ui.components.RetroTopBar
import se.joynes.aiterminalhub.ui.navigation.SessionTabBar
import se.joynes.aiterminalhub.ui.screen.terminal.FontSizeControl
import se.joynes.aiterminalhub.ui.screen.terminal.SpecialKeyBar
import se.joynes.aiterminalhub.ui.theme.*

private const val KEYBOARD_INACTIVITY_MS = 10_000L

@Composable
fun SessionHostScreen(
    serverId: Long,
    onBack: () -> Unit,
    onAddProject: () -> Unit,
    viewModel: SessionHostViewModel = hiltViewModel()
) {
    val projectTabs by viewModel.projectTabs.collectAsState()
    val sessions by viewModel.sessionManager.sessions.collectAsState()
    val activeId by viewModel.activeId.collectAsState()
    val emulator by viewModel.activeEmulator.collectAsState()
    val clipboardManager = LocalClipboardManager.current

    val focusRequester = remember { FocusRequester() }
    var keyboardVisible by remember { mutableStateOf(true) }
    var lastActivityMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showSessionHistory by remember { mutableStateOf(false) }

    LaunchedEffect(serverId) { viewModel.initForServer(serverId) }

    LaunchedEffect(emulator) {
        if (emulator != null) {
            focusRequester.requestFocus()
            keyboardVisible = true
        }
    }

    LaunchedEffect(lastActivityMs) {
        delay(KEYBOARD_INACTIVITY_MS)
        keyboardVisible = false
    }

    fun markActivity() {
        lastActivityMs = System.currentTimeMillis()
        keyboardVisible = true
    }

    if (showSessionHistory) {
        SessionHistorySheet(
            sessions = sessions,
            activeId = activeId,
            onSelect = { viewModel.switchToSession(it); focusRequester.requestFocus() },
            onClose = { id ->
                val tab = projectTabs.firstOrNull { it.sessionId == id } ?: return@SessionHistorySheet
                viewModel.closeSession(tab.projectId, id)
            },
            onMoveUp = { idx -> viewModel.moveSession(idx, idx - 1) },
            onMoveDown = { idx -> viewModel.moveSession(idx, idx + 1) },
            onDismiss = { showSessionHistory = false }
        )
    }

    Scaffold(
        topBar = {
            RetroTopBar(title = "TERMINAL", onBack = onBack, actions = {
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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

                // Single active terminal pane
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(pass = PointerEventPass.Initial)
                                var moved = false
                                while (true) {
                                    val ev = awaitPointerEvent(pass = PointerEventPass.Initial)
                                    val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                                    if ((c.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                        moved = true
                                    }
                                    if (!c.pressed) {
                                        if (!moved) markActivity()
                                        break
                                    }
                                }
                            }
                        }
                ) {
                    val em = emulator
                    if (em != null) {
                        // key(em) forces Terminal to fully recreate on tab switch, so its
                        // ImeInputView is always wired to the current emulator's onKeyboardInput.
                        // Without this, the old IME connection persists → input goes to the
                        // wrong session and duplicate characters appear.
                        key(em) {
                            Terminal(
                                terminalEmulator = em,
                                modifier = Modifier.fillMaxSize(),
                                keyboardEnabled = true,
                                showSoftKeyboard = keyboardVisible,
                                initialFontSize = 12.sp,
                                focusRequester = focusRequester,
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
                }

                SpecialKeyBar(
                    onKey = {
                        markActivity()
                        viewModel.sendBytesToActive(it.toByteArray(Charsets.UTF_8))
                    },
                    onPaste = {
                        val text = clipboardManager.getText()?.text ?: return@SpecialKeyBar
                        viewModel.sendBytesToActive(text.toByteArray(Charsets.UTF_8))
                    },
                    onKeyboardToggle = {
                        keyboardVisible = !keyboardVisible
                        if (keyboardVisible) markActivity()
                    }
                )
                FontSizeControl(
                    onIncrease = { /* TODO: font size via termlib */ },
                    onDecrease = { /* TODO: font size via termlib */ }
                )
            }
        }
    }
}
