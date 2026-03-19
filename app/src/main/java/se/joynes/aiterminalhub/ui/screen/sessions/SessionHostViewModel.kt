package se.joynes.aiterminalhub.ui.screen.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
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
            val conn = connectToServer(server)
            val session = SshSession(id = conn.sessionId, server = server, isConnected = conn.connected.value)
            _sessions.value = listOf(session)

            if (project != null) {
                val setupCmd = engine.render(server, project)
                val attachCmd = engine.renderAttach(server, project)

                conn.connected.first { it }          // wait for shell channel
                if (setupCmd.isNotBlank()) {
                    conn.runSilent(setupCmd)          // mkdir + tmux new-session, no echo
                }
                if (attachCmd.isNotBlank()) {
                    delay(1000)                       // let login banner finish
                    conn.send("$attachCmd\n")         // tmux attach via interactive shell
                }
            }
        }
    }

    fun onPageChanged(page: Int) {}
}
