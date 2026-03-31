package se.joynes.aiterminalhub.domain

import android.content.Context
import com.termux.terminal.TerminalInputListener
import com.termux.terminal.TerminalSession
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID
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
    val conn: SshConnection?,
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

    fun localProjectPath(projectName: String): String =
        File(File(context.filesDir, "projects"), projectName).absolutePath

    fun register(
        sessionId: String,
        conn: SshConnection,
        projectName: String,
        projectId: Long = 0L,
        isTmux: Boolean = false,
        tmuxSessionName: String? = null
    ) {
        if (entries.containsKey(sessionId)) return
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        val terminalClient = TerminalSessionClientImpl(context) { changedSession ->
            _screenUpdates.tryEmit(changedSession)
        }
        val terminalSession = TerminalSession.createRemoteSession(5000, terminalClient, object : TerminalInputListener {
            override fun onTerminalInput(data: ByteArray, offset: Int, count: Int): Boolean {
                if (count <= 0) return true
                if (shouldSuppressTerminalReply(data, offset, count)) return true
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
                    logger.log(LogLevel.WARN, TAG, "SSH lost: $projectName — notifying terminal")
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
        logger.log(LogLevel.INFO, TAG, "Session registered: $projectName")

        if (_activeId.value == null) switchTo(TerminalSessionId(sessionId))
    }

    fun registerLocal(
        projectName: String,
        projectId: Long = 0L,
        startupCommands: List<String> = emptyList()
    ) {
        if (entries.values.any { it.meta.projectId == projectId }) return
        val sessionId = "local-${projectId}-${UUID.randomUUID()}"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val terminalClient = TerminalSessionClientImpl(context) { changedSession ->
            _screenUpdates.tryEmit(changedSession)
        }

        val projectDir = File(localProjectPath(projectName)).apply { mkdirs() }
        val shellPath = if (File("/system/bin/sh").exists()) "/system/bin/sh" else "/bin/sh"
        val env = arrayOf(
            "TERM=xterm-256color",
            "HOME=${context.filesDir.absolutePath}",
            "PATH=${System.getenv("PATH") ?: "/system/bin:/system/xbin"}"
        )
        val terminalSession = TerminalSession(
            shellPath,
            projectDir.absolutePath,
            arrayOf(shellPath),
            env,
            5000,
            terminalClient
        )

        val now = System.currentTimeMillis()
        val meta = TerminalSessionMeta(
            id = TerminalSessionId(sessionId),
            projectName = projectName,
            projectId = projectId,
            isTmux = false,
            isConnected = true,
            hasUnreadOutput = false,
            previewLines = emptyList(),
            lastOpenedAt = now,
            createdAt = now
        )
        entries[sessionId] = SessionEntry(meta, null, terminalSession, scope, null)
        publishSessions()
        logger.log(LogLevel.INFO, TAG, "Local session registered: $projectName cwd=${projectDir.absolutePath}")

        scope.launch {
            while (terminalSession.emulator == null) delay(50)
            delay(150)
            startupCommands.filter { it.isNotBlank() }.forEach { command ->
                val payload = if (command.endsWith("\n")) command else "$command\n"
                val bytes = payload.toByteArray(StandardCharsets.UTF_8)
                terminalSession.write(bytes, 0, bytes.size)
                delay(100)
            }
        }

        if (_activeId.value == null) switchTo(TerminalSessionId(sessionId))
    }

    fun switchTo(id: TerminalSessionId) {
        val entry = entries[id.value] ?: return
        val updated = entry.copy(meta = entry.meta.copy(lastOpenedAt = System.currentTimeMillis()))
        entries[id.value] = updated
        _activeId.value = id
        _activeSession.value = updated.terminalSession
        publishSessions()
    }

    fun close(id: TerminalSessionId) {
        val entry = entries.remove(id.value) ?: return
        val closedProjectName = entry.meta.projectName
        val closedMeta = entry.meta.copy(isConnected = false)
        _closedSessions.value = (_closedSessions.value + closedMeta).takeLast(50)
        entry.scope.coroutineContext[Job]?.cancel()
        if (entry.conn != null && entry.meta.isTmux && !entry.tmuxSessionName.isNullOrBlank()) {
            val tmuxSession = entry.tmuxSessionName
            entry.conn.send("tmux kill-session -t '${tmuxSession.replace("'", "'\\''")}'\n")
            logger.log(LogLevel.INFO, TAG, "Requested tmux kill for close: sessionId=${id.value} tmuxSession=$tmuxSession")
        }
        if (entry.conn != null) {
            sshManager.destroySession(id.value)
        } else {
            entry.terminalSession.finishIfRunning()
        }
        publishSessions()

        if (_activeId.value == id) {
            val next = entries.keys.firstOrNull()
            _activeId.value = next?.let { TerminalSessionId(it) }
            _activeSession.value = next?.let { entries[it]?.terminalSession }
        }
        logger.log(LogLevel.INFO, TAG, "Session closed: $closedProjectName")
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

    /** Returns the live SSH connection for the given project, or null if not connected. */
    fun getConnectionForProject(projectId: Long): SshConnection? =
        entries.values.firstOrNull { it.meta.projectId == projectId }?.conn

    /** Send bytes to the active SSH connection (not the dummy subprocess). */
    fun sendBytesToActive(bytes: ByteArray) {
        val id = _activeId.value?.value ?: return
        val entry = entries[id] ?: return
        if (entry.conn != null) {
            entry.conn.sendBytes(bytes)
        } else {
            entry.terminalSession.write(bytes, 0, bytes.size)
        }
    }

    fun resizeActivePty(cols: Int, rows: Int) {
        val id = _activeId.value?.value ?: return
        entries[id]?.conn?.resizePty(cols, rows)
    }

    private fun publishSessions() {
        _sessions.value = entries.values.map { entry ->
            entry.meta.copy(isConnected = entry.conn?.connected?.value ?: entry.terminalSession.isRunning)
        }
    }

    private fun shouldSuppressTerminalReply(data: ByteArray, offset: Int, count: Int): Boolean {
        if (count < 2 || data[offset] != 0x1b.toByte()) return false
        val text = String(data, offset, count, StandardCharsets.UTF_8)
        return when {
            text.startsWith("\u001b[8;") && text.endsWith("t") -> true
            text.startsWith("\u001b[4;") && text.endsWith("t") -> true
            text.startsWith("\u001b[9;") && text.endsWith("t") -> true
            text.startsWith("\u001b[>") && text.endsWith("c") -> true
            text.startsWith("\u001b[?64;") && text.endsWith("c") -> true
            text.startsWith("\u001b]10;") -> true
            text.startsWith("\u001b]11;") -> true
            else -> false
        }
    }

    private fun describeBytes(data: ByteArray, offset: Int, count: Int): String {
        val shown = data.copyOfRange(offset, offset + minOf(count, 24))
        return shown.joinToString(prefix = "[", postfix = if (count > shown.size) " ...]" else "]") {
            "%02x".format(it)
        }
    }

    fun debugSnapshot(): String = buildString {
        append("entries=").append(entries.size)
        append(",active=").append(_activeId.value?.value)
        append(",terminal=").append(_activeSession.value?.let { System.identityHashCode(it) })
        append(",ids=")
        append(entries.keys.joinToString(prefix = "[", postfix = "]"))
    }

    companion object { private const val TAG = "TerminalSessionManager" }
}
