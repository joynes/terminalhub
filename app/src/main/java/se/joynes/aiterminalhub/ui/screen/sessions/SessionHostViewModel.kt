package se.joynes.aiterminalhub.ui.screen.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.connectbot.terminal.TerminalEmulator
import se.joynes.aiterminalhub.data.model.Project
import se.joynes.aiterminalhub.data.repository.ProjectRepository
import se.joynes.aiterminalhub.data.repository.ServerRepository
import se.joynes.aiterminalhub.data.ssh.SshManager
import se.joynes.aiterminalhub.domain.ScriptTemplateEngine
import se.joynes.aiterminalhub.domain.TerminalSessionId
import se.joynes.aiterminalhub.domain.TerminalSessionManager
import se.joynes.aiterminalhub.domain.TerminalSessionMeta
import se.joynes.aiterminalhub.domain.usecase.ConnectToServer
import javax.inject.Inject

@HiltViewModel
class SessionHostViewModel @Inject constructor(
    private val serverRepo: ServerRepository,
    private val projectRepo: ProjectRepository,
    private val connectToServer: ConnectToServer,
    private val sshManager: SshManager,
    private val engine: ScriptTemplateEngine,
    val sessionManager: TerminalSessionManager
) : ViewModel() {

    val sessions: StateFlow<List<TerminalSessionMeta>> = sessionManager.sessions
    val activeId: StateFlow<TerminalSessionId?> = sessionManager.activeId
    val activeEmulator: StateFlow<TerminalEmulator?> = sessionManager.activeEmulator()

    // Projects explicitly closed by the user; filtered out of the DB-driven list so they
    // don't reappear when a new project is added and the Room Flow re-emits.
    private val closedProjectIds = mutableSetOf<Long>()
    // Projects for which a connection has already been initiated.
    private val connectingProjectIds = mutableSetOf<Long>()

    private var serverId: Long = 0L

    fun initForServer(sId: Long) {
        if (serverId == sId) return
        serverId = sId
        viewModelScope.launch {
            projectRepo.getByServer(sId).collect { projects ->
                projects
                    .filter { p -> p.id !in closedProjectIds }
                    .forEach { p -> activateProject(p) }
            }
        }
    }

    private fun activateProject(project: Project) {
        // Already connecting or registered → skip
        val alreadyRegistered = sessions.value.any { it.id.value.contains(project.id.toString()) }
        if (alreadyRegistered || project.id in connectingProjectIds) return
        connectingProjectIds.add(project.id)

        viewModelScope.launch {
            val srv = serverRepo.getById(serverId) ?: return@launch
            val conn = connectToServer(srv)
            val setupCmd = engine.render(srv, project)
            val attachCmd = engine.renderAttach(srv, project)
            conn.connected.first { it }
            if (setupCmd.isNotBlank()) conn.runSilent(setupCmd)
            if (attachCmd.isNotBlank()) {
                delay(1000)
                conn.send("$attachCmd\n")
            }
            sessionManager.register(conn.sessionId, conn, project.name)
        }
    }

    fun switchToSession(id: TerminalSessionId) = sessionManager.switchTo(id)

    fun closeSession(id: TerminalSessionId) {
        val meta = sessions.value.find { it.id == id } ?: return
        // Derive project id from sessions list — mark as closed by name match
        closedProjectIds.addAll(
            sessions.value
                .filter { it.projectName == meta.projectName }
                .mapNotNull { it.id.value.toLongOrNull() }
        )
        sessionManager.close(id)
    }

    fun sendBytesToActive(bytes: ByteArray) = sessionManager.sendBytesToActive(bytes)
}
