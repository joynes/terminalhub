package se.joynes.aiterminalhub.ui.screen.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.termux.terminal.TerminalSession
import se.joynes.aiterminalhub.data.logging.AppLogger
import se.joynes.aiterminalhub.data.logging.LogLevel
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
    val isConnected: Boolean,
    val colorSeed: Int = 0
)

@HiltViewModel
class SessionHostViewModel @Inject constructor(
    private val logger: AppLogger,
    private val serverRepo: ServerRepository,
    private val projectRepo: ProjectRepository,
    private val connectToServer: ConnectToServer,
    private val sshManager: SshManager,
    private val engine: ScriptTemplateEngine,
    val sessionManager: TerminalSessionManager
) : ViewModel() {

    private val instanceId = System.identityHashCode(this)

    // All projects from DB (shown immediately, even while connecting)
    private val _dbProjects = MutableStateFlow<List<Project>>(emptyList())
    private val _allDbProjects = MutableStateFlow<List<Project>>(emptyList())

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
                isConnected = session?.isConnected ?: false,
                colorSeed = p.colorSeed
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activeId: StateFlow<TerminalSessionId?> = sessionManager.activeId
    val activeSession: StateFlow<TerminalSession?> = sessionManager.activeSession()
    val screenUpdates: SharedFlow<TerminalSession> = sessionManager.screenUpdates

    private val connectingProjectIds = mutableSetOf<Long>()
    private val connectingJobs = mutableMapOf<Long, Job>()

    private val _serverId = MutableStateFlow<Long?>(null)
    val serverId: StateFlow<Long?> = _serverId.asStateFlow()

    private var initialized = false

    fun init() {
        if (initialized) return
        initialized = true
        logger.log(LogLevel.INFO, "SessionHostViewModel", "init snapshot=${debugSnapshot()}")
        viewModelScope.launch {
            serverRepo.getAll().collect { servers ->
                val sId = servers.firstOrNull()?.id ?: return@collect
                if (_serverId.value == sId) return@collect
                _serverId.value = sId
                projectRepo.getByServer(sId).collect { projects ->
                    _allDbProjects.value = projects
                    val visible = projects.filter { !sessionManager.isProjectClosed(it.id) }
                    _dbProjects.value = visible
                    visible.forEach { activateProject(it) }
                }
            }
        }
    }

    private fun activateProject(project: Project) {
        if (project.id in connectingProjectIds) return
        // Already registered as a session
        if (sessionManager.sessions.value.any { it.projectName == project.name }) return
        connectingProjectIds.add(project.id)

        connectingJobs[project.id] = viewModelScope.launch {
            val srv = serverRepo.getById(_serverId.value ?: return@launch) ?: return@launch
            val conn = connectToServer(srv)
            val setupCmd    = engine.renderSetup(srv, project)
            val attachCmd   = engine.renderAttach(srv, project)
            val customScript = engine.renderCustomScript(srv, project)
            val aiCmd       = engine.renderAiCommand(project)
            conn.connected.first { it }
            if (sessionManager.isProjectClosed(project.id)) {
                sshManager.destroySession(conn.sessionId)
                return@launch
            }
            // 1. Silent exec: mkdir + create tmux session if needed
            if (setupCmd.isNotBlank()) conn.runSilent(setupCmd)
            // 2. Wait for the interactive shell banner/prompt to settle before sending commands.
            conn.awaitOutputQuiescence(requireNewOutput = true)

            // 3. Attach to tmux session (or plain shell if useTmux=false)
            if (attachCmd.isNotBlank()) {
                conn.send("$attachCmd\n")
                conn.awaitOutputQuiescence()
            }
            if (!sessionManager.isProjectClosed(project.id)) {
                sessionManager.register(
                    conn.sessionId,
                    conn,
                    project.name,
                    project.id,
                    isTmux = project.useTmux,
                    tmuxSessionName = if (project.useTmux) engine.sessionName(project) else null
                )
            } else {
                sshManager.destroySession(conn.sessionId)
                return@launch
            }

            // 4. Wait until attach/plain shell output settles, then run the custom script.
            conn.awaitOutputQuiescence()
            if (customScript.isNotBlank()) {
                conn.send("$customScript\n")
                conn.awaitOutputQuiescence()
            }

            // 5. AI tool last, after prior command output settles.
            if (aiCmd.isNotBlank()) {
                conn.send("$aiCmd\n")
            }
            connectingJobs.remove(project.id)
        }
    }

    fun switchToSession(id: TerminalSessionId) {
        sessionManager.switchTo(id)
    }

    fun closeSession(projectId: Long, sessionId: TerminalSessionId?) {
        sessionManager.markProjectClosed(projectId)
        connectingJobs.remove(projectId)?.cancel()
        _dbProjects.value = _dbProjects.value.filter { it.id != projectId }
        connectingProjectIds.remove(projectId)
        sessionId?.let { sessionManager.close(it) }
    }

    fun reopenSession(projectId: Long) {
        sessionManager.markProjectOpen(projectId)
        val project = _allDbProjects.value.find { it.id == projectId } ?: return
        if (_dbProjects.value.none { it.id == projectId }) {
            _dbProjects.value = _dbProjects.value + project
        }
        activateProject(project)
    }

    fun moveSession(fromIndex: Int, toIndex: Int) = sessionManager.moveSession(fromIndex, toIndex)

    fun sendBytesToActive(bytes: ByteArray) = sessionManager.sendBytesToActive(bytes)
    fun resizeActivePty(cols: Int, rows: Int) = sessionManager.resizeActivePty(cols, rows)

    fun debugSnapshot(): String = buildString {
        append("vm=").append(instanceId)
        append(",initialized=").append(initialized)
        append(",serverId=").append(_serverId.value)
        append(",activeId=").append(activeId.value?.value)
        append(",activeTerminal=").append(activeSession.value?.let { System.identityHashCode(it) })
        append(",dbProjects=").append(_dbProjects.value.size)
        append(",connecting=").append(connectingProjectIds.joinToString(prefix = "[", postfix = "]"))
        append(",ssh={").append(sshManager.debugSnapshot()).append("}")
        append(",terminals={").append(sessionManager.debugSnapshot()).append("}")
    }
}
