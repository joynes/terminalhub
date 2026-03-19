package se.joynes.aiterminalhub.ui.screen.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import se.joynes.aiterminalhub.data.logging.AppLogger
import se.joynes.aiterminalhub.data.logging.LogLevel
import se.joynes.aiterminalhub.data.ssh.SshManager
import javax.inject.Inject

@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val sshManager: SshManager,
    private val logger: AppLogger
) : ViewModel() {
    private val _output = MutableSharedFlow<String>(replay = 200, extraBufferCapacity = 512)
    val output: SharedFlow<String> = _output.asSharedFlow()

    private var currentSessionId: String? = null

    fun attachSession(sessionId: String) {
        currentSessionId = sessionId
        val conn = sshManager.getSession(sessionId)
        logger.log(LogLevel.DEBUG, TAG, "attachSession: $sessionId conn=${conn != null}")
        if (conn == null) {
            logger.log(LogLevel.ERROR, TAG, "No SshConnection found for session $sessionId")
            return
        }
        viewModelScope.launch {
            logger.log(LogLevel.DEBUG, TAG, "Starting output collection for $sessionId")
            conn.output.collect { text ->
                logger.log(LogLevel.TRACE, TAG, "Output chunk ${text.length} bytes → SharedFlow")
                _output.emit(text)
            }
            logger.log(LogLevel.WARN, TAG, "Output collection ended for $sessionId")
        }
    }

    fun onPageReady(sessionId: String) {
        logger.log(LogLevel.INFO, TAG, "WebView page ready for session $sessionId — will start rendering output")
    }

    fun logFromJs(msg: String) {
        logger.log(LogLevel.DEBUG, "xterm.js", msg)
    }

    fun send(data: String) {
        currentSessionId?.let { sshManager.getSession(it)?.send(data) }
    }

    companion object { private const val TAG = "TerminalVM" }
}
