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

    private var serverId: Long = 0L
    private var server: Server? = null

    fun initForServer(sId: Long) {
        if (serverId == sId) return
        serverId = sId
        viewModelScope.launch {
            server = serverRepo.getById(sId) ?: return@launch
            // Observe project list — new projects appear as unconnected tabs automatically
            projectRepo.getByServer(sId).collect { projects ->
                val existing = _tabs.value.associate { it.project.id to it.sessionId }
                _tabs.value = projects.map { p ->
                    ProjectTab(project = p, sessionId = existing[p.id])
                }
            }
        }
    }

    /** Connect the tab at [index] if not already connected, then return its session id. */
    fun activateTab(index: Int) {
        val tab = _tabs.value.getOrNull(index) ?: return
        if (tab.sessionId != null) return   // already connected
        val srv = server ?: return
        viewModelScope.launch {
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
        tab.sessionId?.let { sshManager.destroySession(it) }
        _tabs.value = _tabs.value.toMutableList().also { it.removeAt(index) }
    }
}
