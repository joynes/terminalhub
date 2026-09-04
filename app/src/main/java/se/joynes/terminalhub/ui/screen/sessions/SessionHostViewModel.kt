package se.joynes.terminalhub.ui.screen.sessions

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.termux.terminal.TerminalSession
import java.io.File
import se.joynes.terminalhub.BuildConfig
import se.joynes.terminalhub.data.db.dao.TextInputHistoryDao
import se.joynes.terminalhub.data.db.entity.TextInputHistoryEntity
import se.joynes.terminalhub.data.logging.AppLogger
import se.joynes.terminalhub.data.logging.LogLevel
import se.joynes.terminalhub.data.model.LOCAL_PROJECT_SERVER_ID
import se.joynes.terminalhub.data.model.ProjectTargetType
import se.joynes.terminalhub.data.model.Project
import se.joynes.terminalhub.data.model.Server
import se.joynes.terminalhub.data.repository.ProjectRepository
import se.joynes.terminalhub.data.repository.ServerRepository
import se.joynes.terminalhub.data.runtime.AppRuntimeRepository
import se.joynes.terminalhub.data.runtime.BackgroundSshCommand
import se.joynes.terminalhub.data.runtime.BackgroundSshEvent
import se.joynes.terminalhub.data.runtime.BackgroundSshMode
import se.joynes.terminalhub.data.runtime.BackgroundSshModeController
import se.joynes.terminalhub.data.security.HostKeyChallenge
import se.joynes.terminalhub.data.security.HostKeyCheckResult
import se.joynes.terminalhub.data.security.HostKeyChallengeKind
import se.joynes.terminalhub.data.security.KnownHostRepository
import se.joynes.terminalhub.data.settings.AppSettingsRepository
import se.joynes.terminalhub.data.ssh.SshManager
import se.joynes.terminalhub.domain.ScriptTemplateEngine
import se.joynes.terminalhub.domain.TerminalSessionId
import se.joynes.terminalhub.domain.TerminalSessionManager
import se.joynes.terminalhub.domain.TerminalSessionMeta
import se.joynes.terminalhub.domain.usecase.ConnectToServer
import se.joynes.terminalhub.service.BackgroundSshService
import javax.inject.Inject

/**
 * Represents one tab in the tab bar.
 * [sessionId] is null while the SSH connection is still being established.
 */
data class ProjectTabState(
    val projectId: Long,
    val projectName: String,
    val sessionId: TerminalSessionId?,
    val isConnected: Boolean,
    val isConnecting: Boolean = false,
    val colorSeed: Int = 0,
    val usesTmux: Boolean = false,
    val targetType: ProjectTargetType = ProjectTargetType.SSH,
    val connectionError: String? = null
)

internal sealed interface SshConnectionAttemptResult {
    data object Connected : SshConnectionAttemptResult
    data class Failed(val message: String) : SshConnectionAttemptResult
}

internal suspend fun awaitSshConnectionAttempt(
    connected: StateFlow<Boolean>,
    lastErrorMessage: StateFlow<String?>,
    timeoutMs: Long
): SshConnectionAttemptResult = withTimeoutOrNull(timeoutMs) {
    combine(connected, lastErrorMessage) { isConnected, error ->
        when {
            isConnected -> SshConnectionAttemptResult.Connected
            !error.isNullOrBlank() -> SshConnectionAttemptResult.Failed(error)
            else -> null
        }
    }.first { it != null }!!
} ?: SshConnectionAttemptResult.Failed(
    "Connection timed out. Check phone network, Tailscale, host, and SSH port, then try again."
)

internal fun recoveryProjectsInPriorityOrder(
    projects: List<Project>,
    preferredProjectId: Long?
): List<Project> {
    val primary = projects.firstOrNull { it.id == preferredProjectId } ?: projects.firstOrNull()
    return if (primary == null) emptyList() else listOf(primary) + projects.filterNot { it.id == primary.id }
}

data class HostKeyPrompt(
    val challenge: HostKeyChallenge,
    val projectIds: Set<Long>
)

internal fun groupHostKeyChallenges(challenges: Map<Long, HostKeyChallenge>): List<HostKeyPrompt> =
    challenges.entries
        .groupBy({ it.value }, { it.key })
        .map { (challenge, projectIds) -> HostKeyPrompt(challenge, projectIds.toSet()) }

internal fun shouldShowBackgroundSshRecommendation(
    recommendationHandled: Boolean,
    keepSshActiveInBackground: Boolean,
    connectedRemoteSessionCount: Int
): Boolean = !recommendationHandled && !keepSshActiveInBackground && connectedRemoteSessionCount > 0

internal fun shouldSwitchToReplacementSession(
    autoSwitch: Boolean,
    replacementSessionId: TerminalSessionId?,
    activeSessionId: TerminalSessionId?
): Boolean = autoSwitch || (replacementSessionId != null && replacementSessionId == activeSessionId)

internal fun reconnectFeedbackMessage(projectNames: List<String>): String = when (projectNames.size) {
    0 -> "All SSH tabs are already connected"
    1 -> "Reconnecting ${projectNames.single()}…"
    else -> "Reconnecting ${projectNames.size} tabs in parallel…"
}

data class SessionHomeState(
    val serverCount: Int = 0,
    val projectCount: Int = 0,
    val selectedServer: Server? = null
) {
    val hasServers: Boolean get() = serverCount > 0
}

@HiltViewModel
class SessionHostViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: AppLogger,
    private val serverRepo: ServerRepository,
    private val projectRepo: ProjectRepository,
    private val connectToServer: ConnectToServer,
    private val sshManager: SshManager,
    private val engine: ScriptTemplateEngine,
    val sessionManager: TerminalSessionManager,
    private val textInputHistoryDao: TextInputHistoryDao,
    private val settingsRepository: AppSettingsRepository,
    private val runtimeRepository: AppRuntimeRepository,
    private val knownHosts: KnownHostRepository,
    private val backgroundSshModeController: BackgroundSshModeController
) : ViewModel() {
    private val prefs = context.getSharedPreferences("session_host", Context.MODE_PRIVATE)
    private val tabOrderKey = "project_tab_order"

    private val instanceId = System.identityHashCode(this)

    // Projects represented by currently open, connecting, or recovery tabs.
    private val _dbProjects = MutableStateFlow<List<Project>>(emptyList())
    private val _allDbProjects = MutableStateFlow<List<Project>>(emptyList())
    private val _projectOrder = MutableStateFlow(loadProjectOrder())
    private val connectingProjectIds = MutableStateFlow<Set<Long>>(emptySet())
    private val connectionErrors = MutableStateFlow<Map<Long, String>>(emptyMap())
    private val _hostKeyChallenges = MutableStateFlow<Map<Long, HostKeyChallenge>>(emptyMap())
    val hostKeyPrompts: StateFlow<List<HostKeyPrompt>> = _hostKeyChallenges
        .map(::groupHostKeyChallenges)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val _trustingHostKeys = MutableStateFlow<Set<HostKeyChallenge>>(emptySet())
    val trustingHostKeys: StateFlow<Set<HostKeyChallenge>> = _trustingHostKeys.asStateFlow()
    private val _uiMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val uiMessages: SharedFlow<String> = _uiMessages.asSharedFlow()

    /** Combined tab list: open project tabs merged with live session state. */
    val projectTabs: StateFlow<List<ProjectTabState>> = combine(
        _dbProjects,
        sessionManager.sessions,
        _projectOrder,
        connectingProjectIds,
        connectionErrors
    ) { projects, sessions, projectOrder, connectingIds, errors ->
        val sessionByProjectId = sessions.associateBy { it.projectId }
        projects
            .sortedByProjectOrder(projectOrder)
            .map { p ->
            val session = sessionByProjectId[p.id]
            ProjectTabState(
                projectId = p.id,
                projectName = p.name,
                sessionId = session?.id,
                isConnected = session?.isConnected ?: false,
                isConnecting = p.id in connectingIds,
                colorSeed = p.colorSeed,
                usesTmux = p.targetType == ProjectTargetType.SSH && p.useTmux,
                targetType = p.targetType,
                connectionError = errors[p.id]
            )
            }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activeId: StateFlow<TerminalSessionId?> = sessionManager.activeId
    val activeSession: StateFlow<TerminalSession?> = sessionManager.activeSession()
    val screenUpdates: SharedFlow<TerminalSession> = sessionManager.screenUpdates
    val preferFastResume: StateFlow<Boolean> =
        settingsRepository.settings
            .map { it.preferFastResume }
            .stateIn(viewModelScope, SharingStarted.Eagerly, settingsRepository.settings.value.preferFastResume)
    val executeTextInputOnSend: StateFlow<Boolean> =
        settingsRepository.settings
            .map { it.executeTextInputOnSend }
            .stateIn(viewModelScope, SharingStarted.Eagerly, settingsRepository.settings.value.executeTextInputOnSend)
    val keyBarRows: StateFlow<List<List<String>>> =
        settingsRepository.settings
            .map { it.keyBarRows }
            .stateIn(viewModelScope, SharingStarted.Eagerly, settingsRepository.settings.value.keyBarRows)
    val runtimeState = runtimeRepository.state
    val showBackgroundSshRecommendation: StateFlow<Boolean> = combine(
        settingsRepository.settings,
        sessionManager.sessions,
        runtimeRepository.state
    ) { settings, sessions, runtime ->
        val connectedRemoteCount = sessions.count {
            it.isConnected && it.projectId in runtime.remoteProjectIds
        }
        shouldShowBackgroundSshRecommendation(
            recommendationHandled = settings.backgroundSshRecommendationHandled,
            keepSshActiveInBackground = settings.keepSshActiveInBackground,
            connectedRemoteSessionCount = connectedRemoteCount
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private val connectingJobs = mutableMapOf<Long, Job>()
    private val _serverId = MutableStateFlow<Long?>(null)
    val serverId: StateFlow<Long?> = _serverId.asStateFlow()
    private var selectedServerId: Long? = null

    val homeState: StateFlow<SessionHomeState> = combine(
        serverRepo.getAll(),
        _allDbProjects,
        _serverId
    ) { servers, projects, currentServerId ->
        val selected = servers.firstOrNull { it.id == currentServerId } ?: servers.firstOrNull()
        SessionHomeState(
            serverCount = servers.size,
            projectCount = projects.size,
            selectedServer = selected
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SessionHomeState())

    private var initialized = false

    fun selectServer(serverId: Long?) {
        selectedServerId = serverId
        if (activeId.value == null) {
            _serverId.value = serverId
        }
    }

    fun init() {
        if (initialized) return
        initialized = true
        logger.log(LogLevel.INFO, "SessionHostViewModel", "init snapshot=${debugSnapshot()}")
        runtimeRepository.state.value.lastProcessRestartReason?.let { reason ->
            logger.log(LogLevel.WARN, "SessionRecovery", "SessionHost init after restart: $reason")
        }
        if (BuildConfig.IS_DIAGNOSTIC) {
            viewModelScope.launch { ensureDiagnosticLocalProject() }
        }
        viewModelScope.launch {
            projectRepo.getAll().collect { projects ->
                val previousProjectIds = _allDbProjects.value.map { it.id }.toSet()
                _allDbProjects.value = projects
                syncProjectOrder(projects)
                val newlyAddedIds = if (previousProjectIds.isEmpty()) {
                    emptySet()
                } else {
                    projects.map { it.id }.toSet() - previousProjectIds
                }
                // Open-tab state must not depend on the short-lived in-memory session list.
                // After a device restart that list is empty until recovery reconnects, but the
                // persisted project tabs should remain visible immediately.
                val visible = projects.filter {
                    !sessionManager.isProjectClosed(it.id)
                }
                val preferredActive = runtimeRepository.state.value.recoveryActiveProjectId
                _dbProjects.value = visible
                val recoveryPending = runtimeRepository.state.value.recoveryPending
                if (recoveryPending) {
                    val recoveryRemoteIds = runtimeRepository.state.value.recoveryRemoteProjectIds
                    val recoveryRemoteProjects = visible.filter {
                        it.targetType == ProjectTargetType.SSH && it.id in recoveryRemoteIds
                    }
                    val orderedRecoveryProjects = recoveryProjectsInPriorityOrder(
                        recoveryRemoteProjects,
                        preferredActive
                    )
                    val primaryRecoveryProject = orderedRecoveryProjects.firstOrNull()

                    visible.filter { it.targetType == ProjectTargetType.LOCAL }.forEach { localProject ->
                        activateProject(localProject, autoSwitch = primaryRecoveryProject == null && localProject.id == preferredActive)
                    }

                    // activateProject creates one coroutine and one SSH connection per project.
                    // Invoke every activation immediately so reconnects proceed in parallel while
                    // only the previously active project is allowed to take UI focus.
                    orderedRecoveryProjects.forEachIndexed { index, project ->
                        activateProject(project, autoSwitch = index == 0)
                    }
                } else {
                    visible.filter { it.id in newlyAddedIds }.forEach { project ->
                        activateProject(project, autoSwitch = true)
                    }
                }
            }
        }
        viewModelScope.launch {
            combine(activeId, sessionManager.sessions, _allDbProjects, serverRepo.getAll()) { activeSessionId, sessions, projects, servers ->
                val activeProjectId = sessions.firstOrNull { it.id == activeSessionId }?.projectId
                val activeServerId = projects.firstOrNull {
                    it.id == activeProjectId && it.targetType == ProjectTargetType.SSH
                }?.serverId
                val fallbackServerId = selectedServerId ?: servers.firstOrNull()?.id
                Triple(activeSessionId, activeServerId, fallbackServerId)
            }.collect { (activeSessionId, activeServerId, fallbackServerId) ->
                _serverId.value = if (activeSessionId == null) fallbackServerId else activeServerId
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

    private fun activateProject(
        project: Project,
        autoSwitch: Boolean = false,
        replacementSessionId: TerminalSessionId? = null
    ) {
        if (project.id in connectingProjectIds.value) return
        // Already registered as a session
        if (replacementSessionId == null && sessionManager.sessions.value.any { it.projectId == project.id }) return
        connectionErrors.value = connectionErrors.value - project.id
        connectingProjectIds.value = connectingProjectIds.value + project.id

        connectingJobs[project.id] = viewModelScope.launch {
            try {
                when (project.targetType) {
                    ProjectTargetType.LOCAL -> activateLocalProject(project, autoSwitch)
                    ProjectTargetType.SSH -> activateSshProject(project, autoSwitch, replacementSessionId)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (project.targetType == ProjectTargetType.SSH) {
                    recordConnectionError(project, error.message ?: "SSH connection failed.")
                }
                logger.log(
                    LogLevel.ERROR,
                    "SessionRecovery",
                    "Activation failed project=${project.name}: ${error.javaClass.simpleName}: ${error.message}"
                )
            } finally {
                connectingJobs.remove(project.id)
                connectingProjectIds.value = connectingProjectIds.value - project.id
            }
        }
    }

    private fun recordConnectionError(project: Project, message: String) {
        connectionErrors.value = connectionErrors.value + (project.id to message)
        logger.log(LogLevel.WARN, "SessionRecovery", "Connection failed project=${project.name}: $message")
    }

    private suspend fun activateSshProject(
        project: Project,
        autoSwitch: Boolean,
        replacementSessionId: TerminalSessionId?
    ) {
        val reason = when {
            runtimeRepository.state.value.recoveryPending &&
                project.id in runtimeRepository.state.value.recoveryRemoteProjectIds -> "process-restart-recovery"
            runtimeRepository.state.value.lastSshDisconnectProjectId == project.id -> "ssh-transport-recovery"
            else -> "normal-activation"
        }
        logger.log(LogLevel.INFO, "SessionRecovery", "Activating SSH project=${project.name} reason=$reason")
        val srv = serverRepo.getById(project.serverId)
        if (srv == null) {
            recordConnectionError(project, "Server configuration was not found.")
            return
        }
        val conn = connectToServer(srv)
        conn.bindProject(project.id, project.name)
        val setupCmd = engine.renderSetup(srv, project)
        val attachCmd = engine.renderAttach(srv, project)
        val customScript = engine.renderCustomScript(srv, project)
        val aiCmd = engine.renderAiCommand(project)
        when (val attempt = awaitSshConnectionAttempt(conn.connected, conn.lastErrorMessage, SSH_CONNECT_TIMEOUT_MS)) {
            SshConnectionAttemptResult.Connected -> {
                connectionErrors.value = connectionErrors.value - project.id
            }
            is SshConnectionAttemptResult.Failed -> {
                conn.hostKeyChallenge.value?.let { challenge ->
                    _hostKeyChallenges.value = _hostKeyChallenges.value + (project.id to challenge)
                }
                recordConnectionError(project, attempt.message)
                sshManager.destroySession(conn.sessionId)
                return
            }
        }
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
            val switchToReplacement = shouldSwitchToReplacementSession(
                autoSwitch = autoSwitch,
                replacementSessionId = replacementSessionId,
                activeSessionId = activeId.value
            )
            sessionManager.register(
                conn.sessionId,
                conn,
                project.name,
                project.id,
                isTmux = project.useTmux,
                tmuxSessionName = if (project.useTmux) engine.sessionName(project) else null,
                lastOpenedAt = project.lastOpenedAt
            )
            replacementSessionId?.let {
                sessionManager.close(it, killTmuxSession = false, selectReplacementIfActive = false)
            }
            if (switchToReplacement) {
                sessionManager.switchTo(TerminalSessionId(conn.sessionId))
            }
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
            conn.send("printf '\\n[TerminalHub] Git clone failed for ${project.name}. Check git/network/path on the server.\\n'\n")
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

    fun trustHostKeyAndReconnect(prompt: HostKeyPrompt) {
        val challenge = prompt.challenge
        if (challenge.kind != HostKeyChallengeKind.UNKNOWN) return
        if (challenge in _trustingHostKeys.value) return
        viewModelScope.launch {
            _trustingHostKeys.value = _trustingHostKeys.value + challenge
            val affectedProjectIds = _hostKeyChallenges.value
                .filterValues { it == challenge }
                .keys
                .ifEmpty { prompt.projectIds }
            val trustResult = runCatching {
                when (knownHosts.check(
                    challenge.endpoint.normalizedHost,
                    challenge.endpoint.port,
                    challenge.presentedAlgorithm,
                    challenge.presentedKeyBytes
                )) {
                    HostKeyCheckResult.Accepted -> Unit
                    is HostKeyCheckResult.Rejected -> knownHosts.trust(challenge)
                    HostKeyCheckResult.CorruptStore -> error("Trusted-host storage is corrupt.")
                    HostKeyCheckResult.InvalidCandidate -> error("The server presented an invalid SSH host key.")
                }
            }
            trustResult.onSuccess {
                _hostKeyChallenges.value = _hostKeyChallenges.value.filterValues { it != challenge }
                val activeProjectId = sessionManager.sessions.value
                    .firstOrNull { it.id == activeId.value }
                    ?.projectId
                affectedProjectIds.forEach { projectId ->
                    reconnectProject(projectId, autoSwitch = projectId == activeProjectId)
                }
                _uiMessages.tryEmit(
                    if (affectedProjectIds.size == 1) "Host trusted — reconnecting tab"
                    else "Host trusted — reconnecting ${affectedProjectIds.size} tabs"
                )
            }.onFailure { error ->
                affectedProjectIds.forEach { projectId ->
                    connectionErrors.value = connectionErrors.value +
                        (projectId to (error.message ?: "Could not save the trusted host key."))
                }
                _uiMessages.tryEmit(error.message ?: "Could not save the trusted host key")
            }
            _trustingHostKeys.value = _trustingHostKeys.value - challenge
        }
    }

    fun dismissHostKeyChallenge(prompt: HostKeyPrompt) {
        _hostKeyChallenges.value = _hostKeyChallenges.value.filterValues { it != prompt.challenge }
    }

    fun dismissBackgroundSshRecommendation() {
        settingsRepository.setBackgroundSshRecommendationHandled()
    }

    fun startRecommendedBackgroundSsh(notificationPermissionGranted: Boolean) {
        settingsRepository.setBackgroundSshRecommendationHandled()
        val transition = backgroundSshModeController.dispatch(
            BackgroundSshEvent.UserStart(
                notificationPermissionGranted = notificationPermissionGranted,
                activeSshSessionCount = runtimeRepository.state.value.remoteProjectIds.size
            )
        )
        when {
            !notificationPermissionGranted -> {
                settingsRepository.setKeepSshActiveInBackground(false)
                _uiMessages.tryEmit("Notification permission is required for background SSH")
            }
            transition.mode == BackgroundSshMode.ACTIVE ||
                transition.mode == BackgroundSshMode.STARTING -> {
                settingsRepository.setKeepSshActiveInBackground(true)
                if (transition.command == BackgroundSshCommand.START_SERVICE) {
                    runCatching {
                        BackgroundSshService.requestStart(context)
                    }.onSuccess {
                        _uiMessages.tryEmit("Background SSH started")
                    }.onFailure {
                        settingsRepository.setKeepSshActiveInBackground(false)
                        backgroundSshModeController.dispatch(BackgroundSshEvent.ServiceStopped)
                        _uiMessages.tryEmit("Could not start background SSH")
                    }
                } else {
                    _uiMessages.tryEmit("Background SSH is already active")
                }
            }
            transition.command != BackgroundSshCommand.START_SERVICE -> {
                settingsRepository.setKeepSshActiveInBackground(false)
                _uiMessages.tryEmit("Open an SSH terminal before starting background SSH")
            }
            else -> _uiMessages.tryEmit("Could not start background SSH")
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
            startupCommands = startupCommands,
            lastOpenedAt = project.lastOpenedAt
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
        connectingProjectIds.value = connectingProjectIds.value - projectId
        connectionErrors.value = connectionErrors.value - projectId
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
                conn.bindProject(project.id, project.name)
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

    /**
     * Opens a specific project by id (e.g. from the project list), switching to it if it is
     * already running or activating a fresh session otherwise. Waits for the project database to
     * load so it works even when called immediately after the screen is created.
     */
    fun openProject(projectId: Long) {
        viewModelScope.launch {
            val project = _allDbProjects
                .first { projects -> projects.any { it.id == projectId } }
                .first { it.id == projectId }
            sessionManager.markProjectOpen(projectId)
            val existing = sessionManager.sessions.value.firstOrNull { it.projectId == projectId }
            if (existing != null) {
                switchToSession(existing.id)
                return@launch
            }
            if (_dbProjects.value.none { it.id == projectId }) {
                _dbProjects.value = _dbProjects.value + project
            }
            projectRepo.updateLastOpenedAt(projectId, System.currentTimeMillis())
            activateProject(project, autoSwitch = true)
        }
    }

    fun reopenSession(projectId: Long) {
        sessionManager.markProjectOpen(projectId)
        val project = _allDbProjects.value.find { it.id == projectId } ?: return
        if (_dbProjects.value.none { it.id == projectId }) {
            _dbProjects.value = _dbProjects.value + project
        }
        viewModelScope.launch { projectRepo.updateLastOpenedAt(projectId, System.currentTimeMillis()) }
        activateProject(project)
    }

    fun reconnectProject(projectId: Long) {
        reconnectProject(projectId, autoSwitch = true, showFeedback = true)
    }

    fun restartTmuxProject(projectId: Long) {
        val project = _allDbProjects.value.find { it.id == projectId }
        if (project == null || project.targetType != ProjectTargetType.SSH || !project.useTmux) {
            _uiMessages.tryEmit("This project does not use remote tmux")
            return
        }
        if (projectId in connectingProjectIds.value) {
            _uiMessages.tryEmit("${project.name} is already connecting")
            return
        }

        val existingSessionId = sessionManager.sessions.value
            .firstOrNull { it.projectId == projectId }
            ?.id
        val wasActive = existingSessionId != null && existingSessionId == activeId.value
        connectionErrors.value = connectionErrors.value - projectId
        connectingProjectIds.value = connectingProjectIds.value + projectId
        logger.log(LogLevel.INFO, "TmuxRestart", "Restart requested project=${project.name}")
        _uiMessages.tryEmit("Restarting tmux for ${project.name}…")

        connectingJobs[projectId] = viewModelScope.launch {
            try {
                val killCommand = engine.renderKillTmux(project)
                val existingConnection = sessionManager.getConnectionForProject(projectId)
                val killedOnExistingConnection = existingConnection != null && runCatching {
                    withTimeoutOrNull(TMUX_KILL_TIMEOUT_MS) {
                        existingConnection.runSilent(killCommand)
                        true
                    } == true
                }.getOrDefault(false)

                if (!killedOnExistingConnection) {
                    val server = serverRepo.getById(project.serverId)
                        ?: error("Server configuration was not found.")
                    val maintenanceConnection = connectToServer(server)
                    try {
                        maintenanceConnection.bindProject(project.id, project.name)
                        when (val attempt = awaitSshConnectionAttempt(
                            maintenanceConnection.connected,
                            maintenanceConnection.lastErrorMessage,
                            SSH_CONNECT_TIMEOUT_MS
                        )) {
                            SshConnectionAttemptResult.Connected -> Unit
                            is SshConnectionAttemptResult.Failed -> error(attempt.message)
                        }
                        val killed = withTimeoutOrNull(TMUX_KILL_TIMEOUT_MS) {
                            maintenanceConnection.runSilent(killCommand)
                            true
                        } == true
                        if (!killed) error("Timed out while stopping the tmux session.")
                    } finally {
                        sshManager.destroySession(maintenanceConnection.sessionId)
                    }
                }

                if (sessionManager.isProjectClosed(projectId)) return@launch
                activateSshProject(
                    project = project,
                    autoSwitch = wasActive,
                    replacementSessionId = existingSessionId
                )
                val replacement = sessionManager.sessions.value.firstOrNull {
                    it.projectId == projectId && it.id != existingSessionId && it.isConnected
                }
                if (replacement == null) {
                    error(
                        connectionErrors.value[projectId]
                            ?: "Tmux stopped, but TerminalHub could not reconnect. Tap Reconnect to try again."
                    )
                }
                _uiMessages.tryEmit("Tmux restarted for ${project.name}")
                logger.log(LogLevel.INFO, "TmuxRestart", "Restart completed project=${project.name}")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val message = error.message ?: "Could not restart tmux."
                connectionErrors.value = connectionErrors.value + (projectId to message)
                _uiMessages.tryEmit(message)
                logger.log(
                    LogLevel.ERROR,
                    "TmuxRestart",
                    "Restart failed project=${project.name}: ${error.javaClass.simpleName}: $message"
                )
            } finally {
                connectingJobs.remove(projectId)
                connectingProjectIds.value = connectingProjectIds.value - projectId
            }
        }
    }

    private fun reconnectProject(projectId: Long, autoSwitch: Boolean, showFeedback: Boolean = false) {
        val project = _allDbProjects.value.find { it.id == projectId } ?: return
        if (projectId in connectingProjectIds.value) {
            if (showFeedback) _uiMessages.tryEmit("${project.name} is already reconnecting")
            return
        }
        val existingSessionId = sessionManager.sessions.value.firstOrNull { it.projectId == projectId }?.id
        if (showFeedback) _uiMessages.tryEmit(reconnectFeedbackMessage(listOf(project.name)))
        sessionManager.markProjectOpen(projectId)
        logger.log(LogLevel.INFO, "SessionRecovery", "Manual reconnect requested for projectId=$projectId")
        viewModelScope.launch { projectRepo.updateLastOpenedAt(projectId, System.currentTimeMillis()) }
        activateProject(
            project,
            autoSwitch = autoSwitch,
            replacementSessionId = existingSessionId
        )
    }

    fun reconnectAllDisconnected() {
        val activeProjectId = sessionManager.sessions.value
            .firstOrNull { it.id == activeId.value }
            ?.projectId
        val disconnected = projectTabs.value.filter { tab ->
            tab.targetType == ProjectTargetType.SSH && !tab.isConnected && !tab.isConnecting
        }
        _uiMessages.tryEmit(reconnectFeedbackMessage(disconnected.map { it.projectName }))
        // Each call launches its own connection job immediately. Keep focus on the tab that was
        // active when reconnect-all started instead of switching tabs as each connection finishes.
        disconnected.forEach { tab ->
            reconnectProject(
                projectId = tab.projectId,
                autoSwitch = tab.projectId == activeProjectId,
                showFeedback = false
            )
        }
    }

    fun moveSession(fromIndex: Int, toIndex: Int) {
        val visibleTabs = projectTabs.value
        if (fromIndex !in visibleTabs.indices || toIndex !in visibleTabs.indices) return
        val visibleIds = visibleTabs.map { it.projectId }.toMutableList()
        val movedId = visibleIds.removeAt(fromIndex)
        visibleIds.add(toIndex, movedId)
        reorderSessions(visibleIds)
    }

    fun reorderSessions(visibleIds: List<Long>) {
        val currentVisibleIds = projectTabs.value.map { it.projectId }
        if (visibleIds.size != currentVisibleIds.size ||
            visibleIds.toSet() != currentVisibleIds.toSet()
        ) {
            return
        }
        val visibleIdSet = visibleIds.toSet()
        val allIds = _allDbProjects.value.sortedByProjectOrder(_projectOrder.value).map { it.id }
        val reorderedVisible = ArrayDeque(visibleIds)
        val mergedOrder = allIds.map { projectId ->
            if (projectId in visibleIdSet) reorderedVisible.removeFirst() else projectId
        }
        persistProjectOrder(mergedOrder)
    }

    fun sendBytesToActive(bytes: ByteArray) = sessionManager.sendBytesToActive(bytes)

    fun pasteTextToActive(text: String): TerminalSessionId? = sessionManager.pasteTextToActive(text)

    fun sendBytesToSession(id: TerminalSessionId, bytes: ByteArray) =
        sessionManager.sendBytesToSession(id, bytes)

    /**
     * Paste first, wait until the complete bracketed-paste sequence is flushed, and only then
     * start the delay before Enter. ViewModel scope keeps the submission alive across IME-driven
     * recomposition or configuration changes.
     */
    fun sendTextInputToActive(text: String, executeImmediately: Boolean) {
        val submission = terminalTextInputSubmission(text, executeImmediately)
        val targetSessionId = sessionManager.pasteTextToActive(submission.pasteText) ?: return
        if (submission.sendEnter) {
            viewModelScope.launch {
                sessionManager.awaitPendingWrites(targetSessionId)
                delay(submission.enterDelayMs)
                sessionManager.sendBytesToSession(
                    targetSessionId,
                    byteArrayOf('\r'.code.toByte())
                )
            }
        }
    }
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

    fun setPreferFastResume(enabled: Boolean) = settingsRepository.setPreferFastResume(enabled)

    fun debugSnapshot(): String = buildString {
        append("vm=").append(instanceId)
        append(",initialized=").append(initialized)
        append(",serverId=").append(_serverId.value)
        append(",activeId=").append(activeId.value?.value)
        append(",activeTerminal=").append(activeSession.value?.let { System.identityHashCode(it) })
        append(",dbProjects=").append(_dbProjects.value.size)
        append(",connecting=").append(connectingProjectIds.value.joinToString(prefix = "[", postfix = "]"))
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

    private companion object {
        const val SSH_CONNECT_TIMEOUT_MS = 15_000L
        const val TMUX_KILL_TIMEOUT_MS = 10_000L
    }
}
