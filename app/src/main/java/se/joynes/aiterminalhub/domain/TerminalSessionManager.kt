package se.joynes.aiterminalhub.domain

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.connectbot.terminal.TerminalEmulator
import org.connectbot.terminal.TerminalEmulatorFactory
import se.joynes.aiterminalhub.data.logging.AppLogger
import se.joynes.aiterminalhub.data.logging.LogLevel
import se.joynes.aiterminalhub.data.ssh.SshConnection
import se.joynes.aiterminalhub.data.ssh.SshManager
import se.joynes.aiterminalhub.data.ssh.TerminalBackendAdapter
import javax.inject.Inject
import javax.inject.Singleton

data class TerminalSessionId(val value: String)

data class TerminalSessionMeta(
    val id: TerminalSessionId,
    val projectName: String,
    val isConnected: Boolean,
    val hasUnreadOutput: Boolean,
    val previewLines: List<String>
)

private data class SessionEntry(
    val meta: TerminalSessionMeta,
    val conn: SshConnection,
    val emulator: TerminalEmulator,
    val adapter: TerminalBackendAdapter,
    val scope: CoroutineScope
)

@Singleton
class TerminalSessionManager @Inject constructor(
    private val sshManager: SshManager,
    private val logger: AppLogger
) {
    private val _sessions = MutableStateFlow<List<TerminalSessionMeta>>(emptyList())
    val sessions: StateFlow<List<TerminalSessionMeta>> = _sessions.asStateFlow()

    private val _activeId = MutableStateFlow<TerminalSessionId?>(null)
    val activeId: StateFlow<TerminalSessionId?> = _activeId.asStateFlow()

    private val _activeEmulator = MutableStateFlow<TerminalEmulator?>(null)

    private val entries = mutableMapOf<String, SessionEntry>()

    fun activeEmulator(): StateFlow<TerminalEmulator?> = _activeEmulator.asStateFlow()

    fun register(sessionId: String, conn: SshConnection, projectName: String) {
        if (entries.containsKey(sessionId)) return
        val scope = CoroutineScope(SupervisorJob())
        val emulator = TerminalEmulatorFactory.create(
            initialRows = 24,
            initialCols = 80,
            defaultForeground = Color(0xFF00FF41),
            defaultBackground = Color(0xFF0D0D1A),
            onKeyboardInput = { bytes: ByteArray -> conn.sendBytes(bytes) },
            // PTY resize via onResize causes SSH EOF on some servers — disabled for now
            onResize = { _ -> }
        )
        val adapter = TerminalBackendAdapter(conn, emulator, scope)
        adapter.start()

        val meta = TerminalSessionMeta(
            id = TerminalSessionId(sessionId),
            projectName = projectName,
            isConnected = conn.connected.value,
            hasUnreadOutput = false,
            previewLines = emptyList()
        )
        entries[sessionId] = SessionEntry(meta, conn, emulator, adapter, scope)
        _sessions.value = entries.values.map { it.meta }
        logger.log(LogLevel.DEBUG, TAG, "Session registered: $sessionId ($projectName)")

        if (_activeId.value == null) switchTo(TerminalSessionId(sessionId))
    }

    fun switchTo(id: TerminalSessionId) {
        val entry = entries[id.value] ?: return
        _activeId.value = id
        _activeEmulator.value = entry.emulator
        logger.log(LogLevel.DEBUG, TAG, "Switched to session: ${id.value}")
    }

    fun close(id: TerminalSessionId) {
        val entry = entries.remove(id.value) ?: return
        entry.scope.run { /* cancel via SupervisorJob */ }
        sshManager.destroySession(id.value)
        _sessions.value = entries.values.map { it.meta }

        if (_activeId.value == id) {
            val next = entries.keys.firstOrNull()
            _activeId.value = next?.let { TerminalSessionId(it) }
            _activeEmulator.value = next?.let { entries[it]?.emulator }
        }
        logger.log(LogLevel.DEBUG, TAG, "Session closed: ${id.value}")
    }

    fun sendBytesToActive(bytes: ByteArray) {
        val id = _activeId.value?.value ?: return
        sshManager.getSession(id)?.sendBytes(bytes)
    }

    companion object { private const val TAG = "TerminalSessionManager" }
}
