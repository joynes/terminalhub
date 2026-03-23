package se.joynes.aiterminalhub.ui.screen.terminal

import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.termux.view.TerminalView
import se.joynes.aiterminalhub.ui.theme.MegaDriveBg
import se.joynes.aiterminalhub.ui.theme.MegaDrivePrimary
import se.joynes.aiterminalhub.ui.theme.MonoFontFamily

@Composable
fun TerminalScreen(
    viewModel: TerminalViewModel = hiltViewModel()
) {
    val session by viewModel.activeSession.collectAsState()
    val context = LocalContext.current
    val modifierManager = remember { MutableModifierManager() }
    val terminalViewRef = remember { mutableStateOf<TerminalView?>(null) }

    var keyboardVisible by remember { mutableStateOf(true) }

    fun showKeyboard() {
        val tv = terminalViewRef.value ?: return
        tv.requestFocus()
        context.getSystemService(InputMethodManager::class.java)
            ?.showSoftInput(tv, InputMethodManager.SHOW_IMPLICIT)
    }

    Column(modifier = Modifier.fillMaxSize().background(MegaDriveBg)) {
        val sess = session
        if (sess != null) {
            key(sess) {
                val terminalViewClient = remember(sess) {
                    TerminalViewClientImpl(
                        modifierManager = modifierManager,
                        onSendToSsh = { bytes -> viewModel.sendBytes(bytes) },
                        onTerminalTap = {
                            keyboardVisible = true
                            showKeyboard()
                        }
                    )
                }
                LaunchedEffect(sess) {
                    keyboardVisible = true
                    showKeyboard()
                }
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(
                        factory = { ctx ->
                            TerminalView(ctx, null).apply {
                                setTextSize(12)
                                setTerminalViewClient(terminalViewClient)
                                attachSession(sess)
                            }.also { terminalViewRef.value = it }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
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
            modifierManager = modifierManager,
            onKey = {
                keyboardVisible = true
                viewModel.sendBytes(it.toByteArray(Charsets.UTF_8))
            },
            onKeyboardToggle = {
                keyboardVisible = !keyboardVisible
                if (keyboardVisible) showKeyboard() else {
                    val tv = terminalViewRef.value ?: return@SpecialKeyBar
                    context.getSystemService(InputMethodManager::class.java)
                        ?.hideSoftInputFromWindow(tv.windowToken, 0)
                }
            }
        )
        FontSizeControl(
            onIncrease = {},
            onDecrease = {}
        )
    }
}
