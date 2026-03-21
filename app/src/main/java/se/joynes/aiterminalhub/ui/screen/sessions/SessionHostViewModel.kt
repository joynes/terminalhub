package se.joynes.aiterminalhub.ui.screen.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import se.joynes.aiterminalhub.data.model.Project
import se.joynes.aiterminalhub.data.model.Server
import se.joynes.aiterminalhub.data.repository.ProjectRepository
import se.joynes.aiterminalhub.data.repository.ServerRepository
import se.joynes.aiterminalhub.data.ssh.SshManager
import se.joynes.aiterminalhub.domain.ScriptTemplateEngine
import se.joynes.aiterminalhub.domain.usecase.ConnectToServer
import javax.inject.Inject

data class ProjectTab(
    val project: Project,
    val sessionId: String? = null   // null = not yet connected
)

@HiltViewModel
class SessionHostViewModel @Inject constructor(
    private val serverRepo: ServerRepository,
    private val projectRepo: ProjectRepository,
    private val connectToServer: ConnectToServer,
    private val sshManager: SshManager,
    private val engine: ScriptTemplateEngine
) : ViewModel() {

    private val _tabs = MutableStateFlow<List<ProjectTab>>(emptyList())
    val tabs: StateFlow<List<ProjectTab>> = _tabs.asStateFlow()

    // Projects explicitly closed by the user; filtered out of the DB-driven list so they
    // don't reappear when a new project is added and the Room Flow re-emits.
    private val closedProjectIds = mutableSetOf<Long>()

    private var serverId: Long = 0L

    fun initForServer(sId: Long) {
        if (serverId == sId) return
        serverId = sId
        viewModelScope.launch {
            projectRepo.getByServer(sId).collect { projects ->
                val existing = _tabs.value.associate { it.project.id to it.sessionId }
                _tabs.value = projects
                    .filter { p -> p.id !in closedProjectIds }
                    .map { p -> ProjectTab(project = p, sessionId = existing[p.id]) }
            }
        }
    }

    /** Connect the tab at [index] if not already connected. Always reloads the server
     *  from the DB so that edits to the setup script are picked up immediately. */
    fun activateTab(index: Int) {
        val tab = _tabs.value.getOrNull(index) ?: return
        if (tab.sessionId != null) return
        viewModelScope.launch {
            val srv = serverRepo.getById(serverId) ?: return@launch
            val conn = connectToServer(srv)
            val setupCmd = engine.render(srv, tab.project)
            val attachCmd = engine.renderAttach(srv, tab.project)
            conn.connected.first { it }
            if (setupCmd.isNotBlank()) conn.runSilent(setupCmd)
            if (attachCmd.isNotBlank()) {
                delay(1000)
                conn.send("$attachCmd\n")
            }
            _tabs.value = _tabs.value.map { t ->
                if (t.project.id == tab.project.id) t.copy(sessionId = conn.sessionId) else t
            }
        }
    }

    fun closeTab(index: Int) {
        val tab = _tabs.value.getOrNull(index) ?: return
        closedProjectIds.add(tab.project.id)
        tab.sessionId?.let { sshManager.destroySession(it) }
        _tabs.value = _tabs.value.toMutableList().also { it.removeAt(index) }
    }
}
