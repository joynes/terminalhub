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

/**
 * Represents one tab in the tab bar.
 * [sessionId] is null while the SSH connection is still being established.
 */
data class ProjectTabState(
    val projectId: Long,
    val projectName: String,
    val sessionId: TerminalSessionId?,   // null = connecting
    val isConnected: Boolean
)

@HiltViewModel
class SessionHostViewModel @Inject constructor(
    private val serverRepo: ServerRepository,
    private val projectRepo: ProjectRepository,
    private val connectToServer: ConnectToServer,
    private val sshManager: SshManager,
    private val engine: ScriptTemplateEngine,
    val sessionManager: TerminalSessionManager
) : ViewModel() {

    // All projects from DB (shown immediately, even while connecting)
    private val _dbProjects = MutableStateFlow<List<Project>>(emptyList())

    /** Combined tab list: DB projects merged with live session state. */
    val projectTabs: StateFlow<List<ProjectTabState>> = combine(
        _dbProjects,
        sessionManager.sessions
    ) { projects, sessions ->
        val sessionByName = sessions.associateBy { it.projectName }
        projects.map { p ->
            val session = sessionByName[p.name]
            ProjectTabState(
                projectId = p.id,
                projectName = p.name,
                sessionId = session?.id,
                isConnected = session?.isConnected ?: false
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activeId: StateFlow<TerminalSessionId?> = sessionManager.activeId
    val activeEmulator: StateFlow<TerminalEmulator?> = sessionManager.activeEmulator()

    private val closedProjectIds = mutableSetOf<Long>()
    private val connectingProjectIds = mutableSetOf<Long>()

    private var serverId: Long = 0L

    fun initForServer(sId: Long) {
        if (serverId == sId) return
        serverId = sId
        viewModelScope.launch {
            projectRepo.getByServer(sId).collect { projects ->
                val visible = projects.filter { it.id !in closedProjectIds }
                _dbProjects.value = visible
                visible.forEach { activateProject(it) }
            }
        }
    }

    private fun activateProject(project: Project) {
        if (project.id in connectingProjectIds) return
        // Already registered as a session
        if (sessionManager.sessions.value.any { it.projectName == project.name }) return
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

    fun switchToSession(id: TerminalSessionId) {
        sessionManager.switchTo(id)
    }

    fun closeSession(projectId: Long, sessionId: TerminalSessionId?) {
        closedProjectIds.add(projectId)
        _dbProjects.value = _dbProjects.value.filter { it.id != projectId }
        connectingProjectIds.remove(projectId)
        sessionId?.let { sessionManager.close(it) }
    }

    fun sendBytesToActive(bytes: ByteArray) = sessionManager.sendBytesToActive(bytes)
}
