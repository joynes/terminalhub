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
    val previewLines: List<String>,
    val lastOpenedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
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

    /** Sessions sorted by lastOpenedAt descending (most recent first). */
    val sessionHistory: StateFlow<List<TerminalSessionMeta>> get() = _sessions

    private val _activeId = MutableStateFlow<TerminalSessionId?>(null)
    val activeId: StateFlow<TerminalSessionId?> = _activeId.asStateFlow()

    private val _activeEmulator = MutableStateFlow<TerminalEmulator?>(null)

    // LinkedHashMap preserves insertion order (tab bar order)
    private val entries = LinkedHashMap<String, SessionEntry>()

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

        val now = System.currentTimeMillis()
        val meta = TerminalSessionMeta(
            id = TerminalSessionId(sessionId),
            projectName = projectName,
            isConnected = conn.connected.value,
            hasUnreadOutput = false,
            previewLines = emptyList(),
            lastOpenedAt = now,
            createdAt = now
        )
        entries[sessionId] = SessionEntry(meta, conn, emulator, adapter, scope)
        publishSessions()
        logger.log(LogLevel.DEBUG, TAG, "Session registered: $sessionId ($projectName)")

        if (_activeId.value == null) switchTo(TerminalSessionId(sessionId))
    }

    fun switchTo(id: TerminalSessionId) {
        val entry = entries[id.value] ?: return
        val updated = entry.copy(meta = entry.meta.copy(lastOpenedAt = System.currentTimeMillis()))
        entries[id.value] = updated
        _activeId.value = id
        _activeEmulator.value = updated.emulator
        publishSessions()
        logger.log(LogLevel.DEBUG, TAG, "Switched to session: ${id.value}")
    }

    fun close(id: TerminalSessionId) {
        val entry = entries.remove(id.value) ?: return
        entry.scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        sshManager.destroySession(id.value)
        publishSessions()

        if (_activeId.value == id) {
            val next = entries.keys.firstOrNull()
            _activeId.value = next?.let { TerminalSessionId(it) }
            _activeEmulator.value = next?.let { entries[it]?.emulator }
        }
        logger.log(LogLevel.DEBUG, TAG, "Session closed: ${id.value}")
    }

    /** Move session at [fromIndex] to [toIndex] in tab bar order. */
    fun moveSession(fromIndex: Int, toIndex: Int) {
        val keys = entries.keys.toMutableList()
        if (fromIndex !in keys.indices || toIndex !in keys.indices) return
        val moved = keys.removeAt(fromIndex)
        keys.add(toIndex, moved)
        val reordered = LinkedHashMap<String, SessionEntry>()
        keys.forEach { k -> entries[k]?.let { reordered[k] = it } }
        entries.clear()
        entries.putAll(reordered)
        publishSessions()
    }

    fun sendBytesToActive(bytes: ByteArray) {
        val id = _activeId.value?.value ?: return
        sshManager.getSession(id)?.sendBytes(bytes)
    }

    private fun publishSessions() {
        _sessions.value = entries.values.map { it.meta }
    }

    companion object { private const val TAG = "TerminalSessionManager" }
}
