package se.joynes.aiterminalhub.ui.screen.terminal

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
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
                            // Post fit+focus after Android has laid out the WebView
                            view?.post {
                                view.evaluateJavascript("window.termFit()", null)
                                view.evaluateJavascript("term.focus()", null)
                                view.requestFocus()
                            }
                            pageReady = true
                        }
                    }
                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun send(data: String) { viewModel.send(data) }
                        @JavascriptInterface
                        fun log(msg: String) { viewModel.logFromJs(msg) }
                    }, "Android")
                    loadUrl("file:///android_asset/terminal/xterm.html")
                    webView = this
                }
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )
        SpecialKeyBar(onKey = { viewModel.send(it) })
        FontSizeControl(
            onIncrease = { webView?.evaluateJavascript("window.termFontSize(1)", null) },
            onDecrease = { webView?.evaluateJavascript("window.termFontSize(-1)", null) }
        )
    }
}
