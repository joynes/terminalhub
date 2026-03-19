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
    val output by viewModel.output.collectAsState("")

    LaunchedEffect(sessionId) { viewModel.attachSession(sessionId) }

    LaunchedEffect(output) {
        val web = webView ?: return@LaunchedEffect
        val escaped = output
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
        web.post { web.evaluateJavascript("window.termWrite('$escaped')", null) }
    }

    Column(modifier = Modifier.fillMaxSize().background(MegaDriveBg)) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = WebViewClient()
                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun send(data: String) { viewModel.send(data) }
                    }, "Android")
                    loadUrl("file:///android_asset/terminal/xterm.html")
                    webView = this
                }
            },
            modifier = Modifier.weight(1f).fillMaxWidth()
        )
        SpecialKeyBar(onKey = { viewModel.send(it) })
        FontSizeControl(
            onIncrease = { webView?.evaluateJavascript("window.termFontSize(1)", null) },
            onDecrease = { webView?.evaluateJavascript("window.termFontSize(-1)", null) }
        )
    }
}
