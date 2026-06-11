package se.joynes.aiterminalhub.ui.screen.servers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import se.joynes.aiterminalhub.data.db.entity.ServerEntity
import se.joynes.aiterminalhub.data.model.Server
import se.joynes.aiterminalhub.data.repository.ServerRepository
import se.joynes.aiterminalhub.data.security.SecurePrefsManager
import se.joynes.aiterminalhub.data.ssh.SshManager
import javax.inject.Inject

enum class SshTestStatus {
    Idle,
    Testing,
    Success,
    Failure
}

data class AddEditServerState(
    val name: String = "",
    val host: String = "",
    val port: String = "22",
    val username: String = "",
    val password: String = "",
    val projectsFolder: String = "~/aiterminalhub",
    val setupScript: String = ServerEntity.DEFAULT_SETUP_SCRIPT,
    val sshTestStatus: SshTestStatus = SshTestStatus.Idle,
    val sshTestMessage: String = "",
    val saved: Boolean = false
)

@HiltViewModel
class AddEditServerViewModel @Inject constructor(
    private val repo: ServerRepository,
    private val securePrefs: SecurePrefsManager,
    private val sshManager: SshManager
) : ViewModel() {
    private val _state = MutableStateFlow(AddEditServerState())
    val state: StateFlow<AddEditServerState> = _state.asStateFlow()

    private var editingId: Long? = null

    fun loadServer(id: Long?) {
        if (id == null) return
        viewModelScope.launch {
            val server = repo.getById(id) ?: return@launch
            editingId = id
            _state.value = AddEditServerState(
                name = server.name,
                host = server.host,
                port = server.port.toString(),
                username = server.username,
                password = securePrefs.getPassword(id) ?: "",
                projectsFolder = server.projectsFolder,
                setupScript = server.setupScript
            )
        }
    }

    fun update(block: AddEditServerState.() -> AddEditServerState) {
        _state.value = _state.value
            .block()
            .copy(sshTestStatus = SshTestStatus.Idle, sshTestMessage = "")
    }

    fun save() {
        viewModelScope.launch {
            val s = _state.value
            val server = Server(
                id = editingId ?: 0L,
                name = s.name.ifBlank { s.host },
                host = s.host,
                port = s.port.toIntOrNull() ?: 22,
                username = s.username,
                projectsFolder = s.projectsFolder,
                setupScript = s.setupScript
            )
            // For edits: save credentials before DB update so the Room flow never
            // sees the server without credentials already in place.
            if (editingId != null && s.password.isNotBlank()) {
                securePrefs.savePassword(editingId!!, s.password)
            }
            val savedId = if (editingId != null) {
                repo.update(server); editingId!!
            } else {
                // New server: DB generates the ID, save credentials immediately after.
                val id = repo.save(server)
                if (s.password.isNotBlank()) securePrefs.savePassword(id, s.password)
                id
            }
            _state.value = _state.value.copy(saved = true)
        }
    }

    fun testSshConnection() {
        val s = _state.value
        if (s.host.isBlank() || s.username.isBlank()) return

        viewModelScope.launch {
            _state.value = _state.value.copy(
                sshTestStatus = SshTestStatus.Testing,
                sshTestMessage = "Testing SSH..."
            )

            val server = Server(
                id = editingId ?: 0L,
                name = s.name.ifBlank { s.host },
                host = s.host.trim(),
                port = s.port.toIntOrNull() ?: 22,
                username = s.username.trim(),
                projectsFolder = s.projectsFolder,
                setupScript = s.setupScript
            )
            val password = s.password.ifBlank {
                editingId?.let { securePrefs.getPassword(it) }.orEmpty()
            }
            val privateKey = editingId?.let { securePrefs.getPrivateKey(it) }
            val conn = sshManager.createSession(server, password, privateKey)
            try {
                var failureMessage: String? = null
                val connected = withTimeoutOrNull<Boolean>(8_000) {
                    while (true) {
                        if (conn.connected.value) return@withTimeoutOrNull true
                        val error = conn.lastErrorMessage.value
                        if (!error.isNullOrBlank()) {
                            failureMessage = error
                            return@withTimeoutOrNull false
                        }
                        delay(100)
                    }
                    false
                } ?: false
                val message = failureMessage ?: "SSH connection timed out. Check Tailscale, host, port, and firewall."
                _state.value = _state.value.copy(
                    sshTestStatus = if (connected) SshTestStatus.Success else SshTestStatus.Failure,
                    sshTestMessage = if (connected) "SSH connection works" else message
                )
            } finally {
                sshManager.destroySession(conn.sessionId)
            }
        }
    }
}
