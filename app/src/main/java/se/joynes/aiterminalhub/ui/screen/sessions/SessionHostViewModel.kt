package se.joynes.aiterminalhub.ui.screen.sessions

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import com.termux.terminal.TerminalSession
import java.io.File
import se.joynes.aiterminalhub.BuildConfig
import se.joynes.aiterminalhub.data.db.dao.TextInputHistoryDao
import se.joynes.aiterminalhub.data.db.entity.TextInputHistoryEntity
import se.joynes.aiterminalhub.data.logging.AppLogger
import se.joynes.aiterminalhub.data.logging.LogLevel
import se.joynes.aiterminalhub.data.model.LOCAL_PROJECT_SERVER_ID
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
    val colorSeed: Int = 0,
    val usesTmux: Boolean = false
)

@HiltViewModel
class SessionHostViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val logger: AppLogger,
    private val serverRepo: ServerRepository,
    private val projectRepo: ProjectRepository,
    private val connectToServer: ConnectToServer,
    private val sshManager: SshManager,
    private val engine: ScriptTemplateEngine,
    val sessionManager: TerminalSessionManager,
    private val textInputHistoryDao: TextInputHistoryDao
) : ViewModel() {
    private val prefs = context.getSharedPreferences("session_host", Context.MODE_PRIVATE)
    private val tabOrderKey = "project_tab_order"

    private val instanceId = System.identityHashCode(this)

    // All projects from DB (shown immediately, even while connecting)
    private val _dbProjects = MutableStateFlow<List<Project>>(emptyList())
    private val _allDbProjects = MutableStateFlow<List<Project>>(emptyList())
    private val _projectOrder = MutableStateFlow(loadProjectOrder())

    /** Combined tab list: DB projects merged with live session state. */
    val projectTabs: StateFlow<List<ProjectTabState>> = combine(
        _dbProjects,
        sessionManager.sessions,
        _projectOrder
    ) { projects, sessions, projectOrder ->
        val sessionByName = sessions.associateBy { it.projectName }
        projects
            .sortedByProjectOrder(projectOrder)
            .map { p ->
            val session = sessionByName[p.name]
            ProjectTabState(
                projectId = p.id,
                projectName = p.name,
                sessionId = session?.id,
                isConnected = session?.isConnected ?: false,
                colorSeed = p.colorSeed,
                usesTmux = p.targetType == ProjectTargetType.SSH && p.useTmux
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
        if (BuildConfig.IS_DIAGNOSTIC) {
            viewModelScope.launch { ensureDiagnosticLocalProject() }
        }
        viewModelScope.launch {
            projectRepo.getAll().collect { projects ->
                _allDbProjects.value = projects
                syncProjectOrder(projects)
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

    private suspend fun ensureDiagnosticLocalProject() {
        if (projectRepo.getAll().first().isNotEmpty()) return
        projectRepo.save(
            Project(
                serverId = LOCAL_PROJECT_SERVER_ID,
                targetType = ProjectTargetType.LOCAL,
                name = "diag-local",
                useTmux = false,
                customScript = "cd {{PROJECT_PATH}}",
                aiCommand = ""
            )
        )
        logger.log(LogLevel.INFO, "SessionHostViewModel", "Created default diagnostic local project")
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
        val gitCloneFailed = setupOutput.contains(ScriptTemplateEngine.GIT_CLONE_FAILED_MARKER)
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

        if (gitCloneFailed) {
            logger.log(
                LogLevel.WARN,
                "SessionHostViewModel",
                "Git clone failed for project=${project.name} url=${project.gitUrl}"
            )
            conn.send("printf '\\n[AITerminalHub] Git clone failed for ${project.name}. Check git/network/path on the server.\\n'\n")
        }

        conn.awaitTransportQuiescence()
        if (!gitCloneFailed && shouldRunStartupCommands && customScript.isNotBlank()) {
            conn.send("$customScript\n")
            conn.awaitTransportQuiescence()
        }

        if (!gitCloneFailed && shouldRunStartupCommands && aiCmd.isNotBlank()) {
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

    fun closeSession(projectId: Long, sessionId: TerminalSessionId?, killTmuxSession: Boolean = false) {
        sessionManager.markProjectClosed(projectId)
        connectingJobs.remove(projectId)?.cancel()
        _dbProjects.value = _dbProjects.value.filter { it.id != projectId }
        connectingProjectIds.remove(projectId)
        sessionId?.let { sessionManager.close(it, killTmuxSession = killTmuxSession) }
    }

    fun closeProject(
        projectId: Long,
        sessionId: TerminalSessionId?,
        killTmuxSession: Boolean = false,
        deleteProject: Boolean = false
    ) {
        viewModelScope.launch {
            val project = _allDbProjects.value.firstOrNull { it.id == projectId }
            if (deleteProject && project != null) {
                moveProjectToTrash(project, sessionId, killTmuxSession)
            }

            closeSession(
                projectId = projectId,
                sessionId = sessionId,
                killTmuxSession = if (deleteProject) false else killTmuxSession
            )

            if (deleteProject && project != null) {
                _projectOrder.value
                    .filterNot { it == projectId }
                    .let(::persistProjectOrder)
                sessionManager.markProjectOpen(projectId)
                projectRepo.delete(project)
            }
        }
    }

    private suspend fun moveProjectToTrash(
        project: Project,
        sessionId: TerminalSessionId?,
        killTmuxSession: Boolean
    ) {
        val trashKey = System.currentTimeMillis().toString()
        when (project.targetType) {
            ProjectTargetType.LOCAL -> {
                val projectDir = File(sessionManager.localProjectPath(project.name))
                if (!projectDir.exists()) return
                val trashDir = File(projectDir.parentFile, ".trash").apply { mkdirs() }
                val trashedDir = File(trashDir, "${project.name}-$trashKey")
                projectDir.renameTo(trashedDir)
            }
            ProjectTargetType.SSH -> {
                val server = serverRepo.getById(project.serverId) ?: return
                val existingConn = sessionId?.let { sessionManager.getConnectionForProject(project.id) }
                val conn = existingConn ?: connectToServer(server)
                conn.connected.first { it }
                if (killTmuxSession && project.useTmux) {
                    conn.runSilent("tmux kill-session -t '${engine.sessionName(project).replace("'", "'\\''")}' 2>/dev/null || true")
                }
                conn.runSilent(engine.renderMoveProjectToTrash(server, project, trashKey))
                if (existingConn == null) {
                    sshManager.destroySession(conn.sessionId)
                }
            }
        }
    }

    fun reopenSession(projectId: Long) {
        sessionManager.markProjectOpen(projectId)
        val project = _allDbProjects.value.find { it.id == projectId } ?: return
        if (_dbProjects.value.none { it.id == projectId }) {
            _dbProjects.value = _dbProjects.value + project
        }
        activateProject(project)
    }

    fun moveSession(fromIndex: Int, toIndex: Int) {
        val visibleTabs = projectTabs.value
        if (fromIndex !in visibleTabs.indices || toIndex !in visibleTabs.indices) return
        val visibleIds = visibleTabs.map { it.projectId }.toMutableList()
        val visibleIdSet = visibleIds.toSet()
        val movedId = visibleIds.removeAt(fromIndex)
        visibleIds.add(toIndex, movedId)
        val allIds = _allDbProjects.value.sortedByProjectOrder(_projectOrder.value).map { it.id }
        val reorderedVisible = ArrayDeque(visibleIds)
        val mergedOrder = allIds.map { projectId ->
            if (projectId in visibleIdSet) reorderedVisible.removeFirst() else projectId
        }
        persistProjectOrder(mergedOrder)
    }

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

    private fun loadProjectOrder(): List<Long> =
        prefs.getString(tabOrderKey, null)
            ?.split(',')
            ?.mapNotNull { it.toLongOrNull() }
            ?: emptyList()

    private fun persistProjectOrder(order: List<Long>) {
        _projectOrder.value = order
        prefs.edit().putString(tabOrderKey, order.joinToString(",")).apply()
    }

    private fun syncProjectOrder(projects: List<Project>) {
        val normalized = projects.sortedByProjectOrder(_projectOrder.value).map { it.id }
        if (normalized != _projectOrder.value) {
            persistProjectOrder(normalized)
        }
    }

    private fun List<Project>.sortedByProjectOrder(order: List<Long>): List<Project> {
        val orderIndex = order.withIndex().associate { it.value to it.index }
        return sortedWith(compareBy<Project> { orderIndex[it.id] ?: Int.MAX_VALUE }.thenBy { it.id })
    }
}
