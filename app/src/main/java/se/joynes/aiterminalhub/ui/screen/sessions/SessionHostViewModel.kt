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
import se.joynes.aiterminalhub.domain.usecase.ApplySessionScript
import se.joynes.aiterminalhub.domain.usecase.ConnectToServer
import javax.inject.Inject

@HiltViewModel
class SessionHostViewModel @Inject constructor(
    private val serverRepo: ServerRepository,
    private val projectRepo: ProjectRepository,
    private val connectToServer: ConnectToServer,
    private val applySessionScript: ApplySessionScript
) : ViewModel() {
    private val _sessions = MutableStateFlow<List<SshSession>>(emptyList())
    val sessions: StateFlow<List<SshSession>> = _sessions.asStateFlow()

    fun initSession(serverId: Long, projectId: Long?) {
        viewModelScope.launch {
            val server = serverRepo.getById(serverId) ?: return@launch
            val conn = connectToServer(server)
            val session = SshSession(id = conn.sessionId, server = server, isConnected = conn.connected.value)
            _sessions.value = listOf(session)
            // Run setup script once the shell channel is open
            val project = projectId?.let { projectRepo.getById(it) }
            if (project != null) {
                conn.connected.first { it } // wait for channel open
                delay(1500)              // let shell finish printing login banner
                applySessionScript(conn.sessionId, server, project)
            }
        }
    }

    fun onPageChanged(page: Int) {}
}
