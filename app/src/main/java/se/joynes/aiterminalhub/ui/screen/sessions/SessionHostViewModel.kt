package se.joynes.aiterminalhub.ui.screen.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import se.joynes.aiterminalhub.data.model.SshSession
import se.joynes.aiterminalhub.data.repository.ProjectRepository
import se.joynes.aiterminalhub.data.repository.ServerRepository
import se.joynes.aiterminalhub.domain.ScriptTemplateEngine
import se.joynes.aiterminalhub.domain.usecase.ConnectToServer
import javax.inject.Inject

@HiltViewModel
class SessionHostViewModel @Inject constructor(
    private val serverRepo: ServerRepository,
    private val projectRepo: ProjectRepository,
    private val connectToServer: ConnectToServer,
    private val engine: ScriptTemplateEngine
) : ViewModel() {
    private val _sessions = MutableStateFlow<List<SshSession>>(emptyList())
    val sessions: StateFlow<List<SshSession>> = _sessions.asStateFlow()

    fun initSession(serverId: Long, projectId: Long?) {
        viewModelScope.launch {
            val server = serverRepo.getById(serverId) ?: return@launch
            val project = projectId?.let { projectRepo.getById(it) }
            // Render setup script as the SSH exec command so it runs silently
            // via ChannelExec+PTY instead of echoing through the interactive shell.
            // Empty script → plain shell (null command).
            val command = project?.let {
                val rendered = engine.render(server, it)
                rendered.ifBlank { null }
            }
            val conn = connectToServer(server, command)
            val session = SshSession(id = conn.sessionId, server = server, isConnected = conn.connected.value)
            _sessions.value = listOf(session)
        }
    }

    fun onPageChanged(page: Int) {}
}
