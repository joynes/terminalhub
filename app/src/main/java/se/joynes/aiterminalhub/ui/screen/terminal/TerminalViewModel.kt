package se.joynes.aiterminalhub.ui.screen.terminal

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.connectbot.terminal.TerminalEmulator
import org.connectbot.terminal.TerminalEmulatorFactory
import se.joynes.aiterminalhub.data.logging.AppLogger
import se.joynes.aiterminalhub.data.logging.LogLevel
import se.joynes.aiterminalhub.data.ssh.SshManager
import javax.inject.Inject

@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val sshManager: SshManager,
    private val logger: AppLogger
) : ViewModel() {

    private val _terminalEmulator = MutableStateFlow<TerminalEmulator?>(null)
    val terminalEmulator: StateFlow<TerminalEmulator?> = _terminalEmulator.asStateFlow()

    private var currentSessionId: String? = null

    fun attachSession(sessionId: String) {
        if (currentSessionId == sessionId && _terminalEmulator.value != null) return
        currentSessionId = sessionId

        val conn = sshManager.getSession(sessionId)
        logger.log(LogLevel.DEBUG, TAG, "attachSession: $sessionId conn=${conn != null}")
        if (conn == null) {
            logger.log(LogLevel.ERROR, TAG, "No SshConnection found for session $sessionId")
            return
        }

        val emulator = TerminalEmulatorFactory.create(
            initialRows = 24,
            initialCols = 80,
            defaultForeground = Color(0xFF00FF41),
            defaultBackground = Color(0xFF0D0D1A),
            onKeyboardInput = { bytes: ByteArray -> conn.sendBytes(bytes) },
            onResize = { dims ->
                conn.resizePty(dims.columns, dims.rows)
                logger.log(LogLevel.DEBUG, TAG, "Terminal resize: ${dims.columns}x${dims.rows}")
            }
        )
        _terminalEmulator.value = emulator

        viewModelScope.launch {
            logger.log(LogLevel.DEBUG, TAG, "Starting output collection for $sessionId")
            conn.output.collect { bytes ->
                emulator.writeInput(bytes)
            }
            logger.log(LogLevel.WARN, TAG, "Output collection ended for $sessionId")
        }
    }

    companion object { private const val TAG = "TerminalVM" }
}
