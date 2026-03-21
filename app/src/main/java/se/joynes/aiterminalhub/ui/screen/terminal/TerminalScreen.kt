package se.joynes.aiterminalhub.ui.screen.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import org.connectbot.terminal.Terminal
import se.joynes.aiterminalhub.ui.theme.MegaDriveBg
import se.joynes.aiterminalhub.ui.theme.MegaDrivePrimary
import se.joynes.aiterminalhub.ui.theme.MonoFontFamily

private const val KEYBOARD_INACTIVITY_MS = 10_000L

@Composable
fun TerminalScreen(
    viewModel: TerminalViewModel = hiltViewModel()
) {
    val terminalEmulator by viewModel.activeEmulator.collectAsState()
    val focusRequester = remember { FocusRequester() }

    var keyboardVisible by remember { mutableStateOf(true) }
    var lastActivityMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(lastActivityMs) {
        delay(KEYBOARD_INACTIVITY_MS)
        keyboardVisible = false
    }

    fun markActivity() {
        lastActivityMs = System.currentTimeMillis()
        keyboardVisible = true
    }

    Column(modifier = Modifier.fillMaxSize().background(MegaDriveBg)) {
        val emulator = terminalEmulator
        if (emulator != null) {
            LaunchedEffect(emulator) {
                focusRequester.requestFocus()
                keyboardVisible = true
            }
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
                Terminal(
                    terminalEmulator = emulator,
                    modifier = Modifier.fillMaxSize(),
                    keyboardEnabled = true,
                    showSoftKeyboard = keyboardVisible,
                    initialFontSize = 12.sp,
                    focusRequester = focusRequester,
                )
            }
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
            onKey = {
                markActivity()
                viewModel.sendBytes(it.toByteArray(Charsets.UTF_8))
            },
            onPaste = {},
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
