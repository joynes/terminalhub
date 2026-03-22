package se.joynes.aiterminalhub.ui.screen.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester

import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.clickable
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

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
            try {
                val method = emulator.javaClass.getMethod("getSnapshot\$lib")
                @Suppress("UNCHECKED_CAST")
                val flow = method.invoke(emulator) as StateFlow<*>
                flow.first { snapshot ->
                    val scrollback = snapshot!!.javaClass.getMethod("getScrollback").invoke(snapshot)
                    (scrollback as? Collection<*>)?.isNotEmpty() == true
                }
                exists = true
            } catch (_: Exception) {}
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

    LaunchedEffect(emulator) {
        if (emulator != null) {
            focusRequester.requestFocus()
            keyboardVisible = true
        }
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
                        key(em, scrollbackExists) {
                            Terminal(
                                terminalEmulator = em,
                                modifier = Modifier.fillMaxSize(),
                                keyboardEnabled = true,
                                showSoftKeyboard = keyboardVisible,
                                initialFontSize = 12.sp,
                                focusRequester = focusRequester,
                                modifierManager = modifierManager,
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
                    modifierManager = modifierManager,
                    onKey = {
                        viewModel.sendBytesToActive(it.toByteArray(Charsets.UTF_8))
                    },
                    onPaste = {
                        val text = clipboardManager.getText()?.text ?: return@SpecialKeyBar
                        viewModel.sendBytesToActive(text.toByteArray(Charsets.UTF_8))
                    },
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
