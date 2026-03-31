package se.joynes.aiterminalhub.ui.screen.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import com.termux.terminal.TerminalSession
import se.joynes.aiterminalhub.data.db.dao.TextInputHistoryDao
import se.joynes.aiterminalhub.data.db.entity.TextInputHistoryEntity
import se.joynes.aiterminalhub.data.logging.AppLogger
import se.joynes.aiterminalhub.data.logging.LogLevel
import se.joynes.aiterminalhub.data.model.ProjectTargetType
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
    val sessionManager: TerminalSessionManager,
    private val textInputHistoryDao: TextInputHistoryDao
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

    fun debugLog(msg: String) {
        logger.log(LogLevel.DEBUG, "TERMDIAG", msg)
    }

    fun init() {
        if (initialized) return
        initialized = true
        logger.log(LogLevel.INFO, "SessionHostViewModel", "init snapshot=${debugSnapshot()}")
        viewModelScope.launch {
            projectRepo.getAll().collect { projects ->
                _allDbProjects.value = projects
                val visible = projects.filter { !sessionManager.isProjectClosed(it.id) }
                val prevIds = _dbProjects.value.map { it.id }.toSet()
                val isFirstLoad = _dbProjects.value.isEmpty() && prevIds.isEmpty()
                _dbProjects.value = visible
                visible.forEach { project ->
                    val isNewlyAdded = !isFirstLoad && project.id !in prevIds
                    activateProject(project, autoSwitch = isNewlyAdded)
                }
            }
        }
        viewModelScope.launch {
            combine(activeId, _allDbProjects) { activeSessionId, projects ->
                val activeProjectId = sessionManager.sessions.value.firstOrNull { it.id == activeSessionId }?.projectId
                projects.firstOrNull { it.id == activeProjectId && it.targetType == ProjectTargetType.SSH }?.serverId
            }.collect { activeServerId ->
                _serverId.value = activeServerId
            }
        }
    }

    private fun activateProject(project: Project, autoSwitch: Boolean = false) {
        if (project.id in connectingProjectIds) return
        // Already registered as a session
        if (sessionManager.sessions.value.any { it.projectId == project.id }) return
        connectingProjectIds.add(project.id)

        connectingJobs[project.id] = viewModelScope.launch {
            try {
                when (project.targetType) {
                    ProjectTargetType.LOCAL -> activateLocalProject(project, autoSwitch)
                    ProjectTargetType.SSH -> activateSshProject(project, autoSwitch)
                }
            } finally {
                connectingJobs.remove(project.id)
                connectingProjectIds.remove(project.id)
            }
        }
    }

    private suspend fun activateSshProject(project: Project, autoSwitch: Boolean) {
        val srv = serverRepo.getById(project.serverId) ?: return
        val conn = connectToServer(srv)
        val setupCmd = engine.renderSetup(srv, project)
        val attachCmd = engine.renderAttach(srv, project)
        val customScript = engine.renderCustomScript(srv, project)
        val aiCmd = engine.renderAiCommand(project)
        conn.connected.first { it }
        if (sessionManager.isProjectClosed(project.id)) {
            sshManager.destroySession(conn.sessionId)
            return
        }
        val setupOutput = if (setupCmd.isNotBlank()) conn.runSilent(setupCmd) else ""
        val shouldRunStartupCommands = if (project.useTmux) {
            setupOutput.contains("TMUX_SESSION_CREATED")
        } else {
            true
        }
        conn.awaitOutputQuiescence(requireNewOutput = true)

        if (attachCmd.isNotBlank()) {
            conn.send("$attachCmd\n")
            conn.awaitTransportQuiescence()
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
            if (autoSwitch) sessionManager.switchTo(TerminalSessionId(conn.sessionId))
        } else {
            sshManager.destroySession(conn.sessionId)
            return
        }

        conn.awaitTransportQuiescence()
        if (shouldRunStartupCommands && customScript.isNotBlank()) {
            conn.send("$customScript\n")
            conn.awaitTransportQuiescence()
        }

        if (shouldRunStartupCommands && aiCmd.isNotBlank()) {
            conn.awaitTransportQuiescence()
            conn.send("$aiCmd\n")
        }
    }

    private fun activateLocalProject(project: Project, autoSwitch: Boolean) {
        val localBasePath = sessionManager.localProjectPath("").trimEnd('/')
        val customScript = engine.renderLocalCustomScript(localBasePath, project)
        val aiCmd = engine.renderAiCommand(project)
        val startupCommands = buildList {
            if (customScript.isNotBlank()) add(customScript)
            if (aiCmd.isNotBlank()) add(aiCmd)
        }
        sessionManager.registerLocal(
            projectName = project.name,
            projectId = project.id,
            startupCommands = startupCommands
        )
        if (autoSwitch) {
            sessionManager.sessions.value.firstOrNull { it.projectId == project.id }?.id?.let { sessionManager.switchTo(it) }
        }
    }

    fun switchToSession(id: TerminalSessionId) {
        sessionManager.switchTo(id)
        val projectId = sessionManager.sessions.value.firstOrNull { it.id == id }?.projectId
        _serverId.value = _allDbProjects.value.firstOrNull {
            it.id == projectId && it.targetType == ProjectTargetType.SSH
        }?.serverId
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

    /** Returns the last 10 text-input history entries for a given project. */
    fun textInputHistory(projectId: Long): Flow<List<String>> =
        textInputHistoryDao.getRecentForProject(projectId)
            .map { list -> list.map { it.text } }

    fun saveTextInput(projectId: Long, text: String) {
        viewModelScope.launch {
            textInputHistoryDao.insert(TextInputHistoryEntity(projectId = projectId, text = text))
            textInputHistoryDao.pruneOldest(projectId)
        }
    }

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
