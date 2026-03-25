package se.joynes.aiterminalhub.domain

import android.content.Context
import com.termux.terminal.TerminalInputListener
import com.termux.terminal.TerminalSession
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import se.joynes.aiterminalhub.data.logging.AppLogger
import se.joynes.aiterminalhub.data.logging.LogLevel
import se.joynes.aiterminalhub.data.ssh.SshConnection
import se.joynes.aiterminalhub.data.ssh.SshManager
import se.joynes.aiterminalhub.data.ssh.TerminalSessionClientImpl
import javax.inject.Inject
import javax.inject.Singleton

data class TerminalSessionId(val value: String)

data class TerminalSessionMeta(
    val id: TerminalSessionId,
    val projectName: String,
    val projectId: Long = 0L,
    val isTmux: Boolean = false,
    val isConnected: Boolean,
    val hasUnreadOutput: Boolean,
    val previewLines: List<String>,
    val lastOpenedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)

private data class SessionEntry(
    val meta: TerminalSessionMeta,
    val conn: SshConnection,
    val terminalSession: TerminalSession,
    val scope: CoroutineScope,
    val tmuxSessionName: String? = null
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

    private val _activeSession = MutableStateFlow<TerminalSession?>(null)
    private val _screenUpdates = MutableSharedFlow<TerminalSession>(extraBufferCapacity = 64)
    val screenUpdates: SharedFlow<TerminalSession> = _screenUpdates.asSharedFlow()

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

    fun activeSession(): StateFlow<TerminalSession?> = _activeSession.asStateFlow()

    fun register(
        sessionId: String,
        conn: SshConnection,
        projectName: String,
        projectId: Long = 0L,
        isTmux: Boolean = false,
        tmuxSessionName: String? = null
    ) {
        if (entries.containsKey(sessionId)) return
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        val terminalClient = TerminalSessionClientImpl(context) { changedSession ->
            _screenUpdates.tryEmit(changedSession)
        }
        val terminalSession = TerminalSession.createRemoteSession(5000, terminalClient, object : TerminalInputListener {
            override fun onTerminalInput(data: ByteArray, offset: Int, count: Int): Boolean {
                if (count <= 0) return true
                conn.sendBytes(data.copyOfRange(offset, offset + count))
                return true
            }
        })

        scope.launch {
            conn.output.collect { bytes ->
                try {
                    terminalSession.appendRemoteOutput(bytes, 0, bytes.size)
                } catch (_: Exception) {}
            }
        }
        scope.launch {
            var wasConnected = false
            conn.connected.collect { connected ->
                if (connected) {
                    wasConnected = true
                } else if (wasConnected && terminalSession.isRunning) {
                    terminalSession.notifyRemoteProcessExit(0)
                }
            }
        }

        val now = System.currentTimeMillis()
        val meta = TerminalSessionMeta(
            id = TerminalSessionId(sessionId),
            projectName = projectName,
            projectId = projectId,
            isTmux = isTmux,
            isConnected = conn.connected.value,
            hasUnreadOutput = false,
            previewLines = emptyList(),
            lastOpenedAt = now,
            createdAt = now
        )
        entries[sessionId] = SessionEntry(meta, conn, terminalSession, scope, tmuxSessionName)
        publishSessions()
        logger.log(LogLevel.DEBUG, TAG, "Session registered: $sessionId ($projectName)")

        if (_activeId.value == null) switchTo(TerminalSessionId(sessionId))
    }

    fun switchTo(id: TerminalSessionId) {
        val entry = entries[id.value] ?: return
        val updated = entry.copy(meta = entry.meta.copy(lastOpenedAt = System.currentTimeMillis()))
        entries[id.value] = updated
        _activeId.value = id
        _activeSession.value = updated.terminalSession
        publishSessions()
        logger.log(LogLevel.DEBUG, TAG, "Switched to session: ${id.value}")
    }

    fun close(id: TerminalSessionId) {
        val entry = entries.remove(id.value) ?: return
        val closedMeta = entry.meta.copy(isConnected = false)
        _closedSessions.value = (_closedSessions.value + closedMeta).takeLast(50)
        entry.scope.coroutineContext[Job]?.cancel()
        sshManager.destroySession(id.value)
        publishSessions()

        if (_activeId.value == id) {
            val next = entries.keys.firstOrNull()
            _activeId.value = next?.let { TerminalSessionId(it) }
            _activeSession.value = next?.let { entries[it]?.terminalSession }
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

    /** Send bytes to the active SSH connection (not the dummy subprocess). */
    fun sendBytesToActive(bytes: ByteArray) {
        val id = _activeId.value?.value ?: return
        entries[id]?.conn?.sendBytes(bytes)
    }

    fun resizeActivePty(cols: Int, rows: Int) {
        val id = _activeId.value?.value ?: return
        entries[id]?.conn?.resizePty(cols, rows)
    }

    private fun publishSessions() {
        _sessions.value = entries.values.map { it.meta }
    }

    companion object { private const val TAG = "TerminalSessionManager" }
}
