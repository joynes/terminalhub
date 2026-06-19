package se.joynes.aiterminal.ui.screen.terminal

import android.os.Build
import android.view.WindowInsets as AndroidWindowInsets
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
import se.joynes.aiterminal.ui.theme.MegaDriveBg
import se.joynes.aiterminal.ui.theme.MegaDrivePrimary
import se.joynes.aiterminal.ui.theme.MonoFontFamily

@Composable
fun TerminalScreen(
    viewModel: TerminalViewModel = hiltViewModel()
) {
    val session by viewModel.activeSession.collectAsState()
    val context = LocalContext.current
    val modifierManager = remember { MutableModifierManager() }
    val terminalViewRef = remember { mutableStateOf<TerminalView?>(null) }
    var lastSyncedCols by remember { mutableIntStateOf(-1) }
    var lastSyncedRows by remember { mutableIntStateOf(-1) }
    var lastViewWidth by remember { mutableIntStateOf(-1) }
    var lastViewHeight by remember { mutableIntStateOf(-1) }

    var keyboardVisible by remember { mutableStateOf(false) }

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

    fun syncRemotePty(tv: TerminalView) {
        val viewChanged = tv.width != lastViewWidth || tv.height != lastViewHeight
        if (viewChanged) {
            lastViewWidth = tv.width
            lastViewHeight = tv.height
            tv.updateSize()
        }
        val emulator = tv.mEmulator ?: return
        val ptyChanged = emulator.mColumns != lastSyncedCols || emulator.mRows != lastSyncedRows
        if (ptyChanged) {
            lastSyncedCols = emulator.mColumns
            lastSyncedRows = emulator.mRows
            viewModel.resizeActivePty(emulator.mColumns, emulator.mRows)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.screenUpdates.collect { changedSession ->
            val tv = terminalViewRef.value ?: return@collect
            if (tv.mTermSession === changedSession) {
                tv.onScreenUpdated()
            }
        }
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
                LaunchedEffect(sess) { keyboardVisible = false }
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(
                        factory = { ctx ->
                            val textSizePx = (14 * ctx.resources.displayMetrics.scaledDensity + 0.5f).toInt()
                            TerminalView(ctx, null).apply {
                                setLayerType(android.view.View.LAYER_TYPE_NONE, null)
                                isFocusable = true
                                isFocusableInTouchMode = true
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    defaultFocusHighlightEnabled = false
                                }
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
                            if (tv.mTermSession !== sess) {
                                tv.attachSession(sess)
                                lastSyncedCols = -1
                                lastSyncedRows = -1
                                lastViewWidth = -1
                                lastViewHeight = -1
                            }
                            tv.setLayerType(android.view.View.LAYER_TYPE_NONE, null)
                            terminalViewRef.value = tv
                            syncRemotePty(tv)
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
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        tv.windowInsetsController?.hide(AndroidWindowInsets.Type.ime())
                    } else {
                        context.getSystemService(InputMethodManager::class.java)
                            ?.hideSoftInputFromWindow(tv.windowToken, 0)
                    }
                }
            }
        )
        FontSizeControl(
            onIncrease = {},
            onDecrease = {}
        )
    }
}
