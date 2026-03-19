package se.joynes.aiterminalhub.ui.screen.terminal

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.View
import android.webkit.JavascriptInterface
import se.joynes.aiterminalhub.BuildConfig
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import se.joynes.aiterminalhub.ui.theme.MegaDriveBg

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TerminalScreen(
    sessionId: String,
    viewModel: TerminalViewModel = hiltViewModel()
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var pageReady by remember { mutableStateOf(false) }

    LaunchedEffect(sessionId) { viewModel.attachSession(sessionId) }

    // Collect SSH output only after xterm.html has finished loading
    LaunchedEffect(pageReady, sessionId) {
        if (!pageReady) return@LaunchedEffect
        val web = webView ?: return@LaunchedEffect
        viewModel.onPageReady(sessionId)
        var firstChunk = true
        viewModel.output.collect { text ->
            val escaped = text
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
            web.post {
                if (firstChunk) {
                    firstChunk = false
                    web.evaluateJavascript("window.termFit()", null)
                }
                web.evaluateJavascript("window.termWrite('$escaped')", null)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(MegaDriveBg)) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    isFocusable = true
                    isFocusableInTouchMode = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            pageReady = true
                        }
                    }
                    // Re-fit after Compose assigns the real height.
                    // onPageFinished fires before layout so fit() gives rows=1 there.
                    // window.termFit is a no-op until xterm.js is initialised, so it's
                    // safe to call unconditionally whenever the height changes.
                    addOnLayoutChangeListener { v, _, _, _, _, _, oldTop, _, oldBottom ->
                        val newH = v.height
                        val oldH = oldBottom - oldTop
                        if (newH > 100 && newH != oldH) {
                            evaluateJavascript("if(window.termFit)window.termFit()", null)
                            evaluateJavascript("if(window.term)term.focus()", null)
                            requestFocus()
                        }
                    }
                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun send(data: String) { viewModel.send(data) }
                        @JavascriptInterface
                        fun log(msg: String) { viewModel.logFromJs(msg) }
                        @JavascriptInterface
                        fun copyToClipboard(text: String) {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("terminal", text))
                        }
                    }, "Android")
                    // Software rendering is needed in debug builds so that Canvas2D
                    // content is captured by screencap / UiAutomator screenshots.
                    // Release builds keep hardware acceleration for full performance.
                    if (BuildConfig.DEBUG) setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                    loadUrl("file:///android_asset/terminal/xterm.html")
                    webView = this
                }
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )
        SpecialKeyBar(
            onKey = { viewModel.send(it) },
            onCopy = { webView?.evaluateJavascript("window.termCopySelection()", null) }
        )
        FontSizeControl(
            onIncrease = { webView?.evaluateJavascript("window.termFontSize(1)", null) },
            onDecrease = { webView?.evaluateJavascript("window.termFontSize(-1)", null) }
        )
    }
}
