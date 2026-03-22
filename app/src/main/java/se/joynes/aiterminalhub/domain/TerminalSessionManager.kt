package se.joynes.aiterminalhub.domain

import android.content.Context
import androidx.compose.ui.graphics.Color
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    val projectId: Long = 0L,
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
    @ApplicationContext private val context: Context,
    private val sshManager: SshManager,
    private val logger: AppLogger
) {
    private val prefs = context.getSharedPreferences("session_manager", Context.MODE_PRIVATE)
    private val _sessions = MutableStateFlow<List<TerminalSessionMeta>>(emptyList())
    val sessions: StateFlow<List<TerminalSessionMeta>> = _sessions.asStateFlow()

    /** Sessions sorted by lastOpenedAt descending (most recent first). */
    val sessionHistory: StateFlow<List<TerminalSessionMeta>> get() = _sessions

    private val _closedSessions = MutableStateFlow<List<TerminalSessionMeta>>(emptyList())
    val closedSessions: StateFlow<List<TerminalSessionMeta>> = _closedSessions.asStateFlow()

    private val _activeId = MutableStateFlow<TerminalSessionId?>(null)
    val activeId: StateFlow<TerminalSessionId?> = _activeId.asStateFlow()

    private val _activeEmulator = MutableStateFlow<TerminalEmulator?>(null)

    // LinkedHashMap preserves insertion order (tab bar order)
    private val entries = LinkedHashMap<String, SessionEntry>()

    // Tracks projects explicitly closed by the user. Persisted in SharedPreferences so
    // closed tabs stay closed across app restarts.
    private val closedProjectIds: MutableSet<Long> =
        prefs.getStringSet("closed_project_ids", emptySet())!!
            .mapTo(mutableSetOf()) { it.toLong() }

    private fun persistClosed() {
        prefs.edit()
            .putStringSet("closed_project_ids", closedProjectIds.map { it.toString() }.toSet())
            .apply()
    }

    fun markProjectClosed(projectId: Long) { closedProjectIds.add(projectId); persistClosed() }
    fun markProjectOpen(projectId: Long)   { closedProjectIds.remove(projectId); persistClosed() }
    fun isProjectClosed(projectId: Long)   = projectId in closedProjectIds

    fun activeEmulator(): StateFlow<TerminalEmulator?> = _activeEmulator.asStateFlow()

    fun register(sessionId: String, conn: SshConnection, projectName: String, projectId: Long = 0L) {
        if (entries.containsKey(sessionId)) return
        val scope = CoroutineScope(SupervisorJob())
        var resizeJob: Job? = null
        val emulator = TerminalEmulatorFactory.create(
            initialRows = 24,
            initialCols = 80,
            defaultForeground = Color(0xFF00FF41),
            defaultBackground = Color(0xFF0D0D1A),
            onKeyboardInput = { bytes: ByteArray -> conn.sendBytes(bytes) },
            // Debounced PTY resize: short delay to coalesce rapid resize events
            // (e.g. keyboard show/hide) without a perceptible lag.
            onResize = { dims ->
                resizeJob?.cancel()
                resizeJob = scope.launch {
                    delay(150)
                    conn.resizePty(dims.columns, dims.rows)
                }
            }
        )
        val adapter = TerminalBackendAdapter(conn, emulator, scope)
        adapter.start()

        val now = System.currentTimeMillis()
        val meta = TerminalSessionMeta(
            id = TerminalSessionId(sessionId),
            projectName = projectName,
            projectId = projectId,
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
        // Keep in history (capped at 50) so user can reopen
        val closedMeta = entry.meta.copy(isConnected = false)
        _closedSessions.value = (_closedSessions.value + closedMeta).takeLast(50)
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
