package se.joynes.aiterminalhub.ui.screen.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import org.connectbot.terminal.Terminal
import se.joynes.aiterminalhub.ui.theme.MegaDriveBg
import se.joynes.aiterminalhub.ui.theme.MegaDrivePrimary
import se.joynes.aiterminalhub.ui.theme.MonoFontFamily

@Composable
fun TerminalScreen(
    sessionId: String,
    viewModel: TerminalViewModel = hiltViewModel()
) {
    val terminalEmulator by viewModel.terminalEmulator.collectAsState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(sessionId) { viewModel.attachSession(sessionId) }

    Column(modifier = Modifier.fillMaxSize().background(MegaDriveBg)) {
        val emulator = terminalEmulator
        if (emulator != null) {
            LaunchedEffect(emulator) { focusRequester.requestFocus() }
            Terminal(
                terminalEmulator = emulator,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                keyboardEnabled = true,
                showSoftKeyboard = true,
                initialFontSize = 12.sp,
                focusRequester = focusRequester,
            )
        } else {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
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
        SpecialKeyBar(
            onKey = { viewModel.sendBytes(it.toByteArray(Charsets.UTF_8)) },
            onCopy = { /* TODO: selection copy via termlib */ }
        )
        FontSizeControl(
            onIncrease = { /* TODO: font size via termlib */ },
            onDecrease = { /* TODO: font size via termlib */ }
        )
    }
}
