package se.joynes.terminalhub.ui.screen.servers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import se.joynes.terminalhub.data.db.entity.ServerEntity
import se.joynes.terminalhub.data.model.Server
import se.joynes.terminalhub.data.repository.ServerRepository
import se.joynes.terminalhub.data.security.SecurePrefsManager
import se.joynes.terminalhub.data.security.SshKeyGenerator
import se.joynes.terminalhub.data.ssh.SshManager
import se.joynes.terminalhub.data.ssh.SshPublicKeyInstaller
import javax.inject.Inject

enum class SshTestStatus {
    Idle,
    Testing,
    Success,
    Failure
}

enum class KeyInstallStatus {
    Idle,
    Installing,
    Success,
    Failure
}

data class AddEditServerState(
    val name: String = "",
    val host: String = "",
    val port: String = "22",
    val username: String = "",
    val password: String = "",
    val privateKey: String = "",
    val publicKey: String = "",
    val hasSavedPrivateKey: Boolean = false,
    val projectsFolder: String = "~/terminalhub",
    val setupScript: String = ServerEntity.DEFAULT_SETUP_SCRIPT,
    val sshTestStatus: SshTestStatus = SshTestStatus.Idle,
    val sshTestMessage: String = "",
    val keyInstallStatus: KeyInstallStatus = KeyInstallStatus.Idle,
    val keyInstallMessage: String = "",
    val saved: Boolean = false
)

@HiltViewModel
class AddEditServerViewModel @Inject constructor(
    private val repo: ServerRepository,
    private val securePrefs: SecurePrefsManager,
    private val sshManager: SshManager,
    private val keyGenerator: SshKeyGenerator,
    private val publicKeyInstaller: SshPublicKeyInstaller
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
                hasSavedPrivateKey = !securePrefs.getPrivateKey(id).isNullOrBlank(),
                projectsFolder = server.projectsFolder,
                setupScript = server.setupScript
            )
        }
    }

    fun update(block: AddEditServerState.() -> AddEditServerState) {
        _state.value = _state.value
            .block()
            .copy(
                sshTestStatus = SshTestStatus.Idle,
                sshTestMessage = "",
                keyInstallStatus = KeyInstallStatus.Idle,
                keyInstallMessage = ""
            )
    }

    fun generateKey() {
        val s = _state.value
        val commentHost = s.host.ifBlank { "server" }.trim()
        val commentUser = s.username.ifBlank { "terminalhub" }.trim()
        val generated = keyGenerator.generate("$commentUser@$commentHost-terminalhub")
        _state.value = s.copy(
            privateKey = generated.privateKeyPem,
            publicKey = generated.publicKeyOpenSsh,
            hasSavedPrivateKey = false,
            keyInstallStatus = KeyInstallStatus.Idle,
            keyInstallMessage = "Generated a new SSH key. Keep the private key in TerminalHub. Copy or install only the public key on the server."
        )
    }

    fun installGeneratedKey() {
        val s = _state.value
        if (s.host.isBlank() || s.username.isBlank()) return
        if (s.publicKey.isBlank()) {
            _state.value = s.copy(
                keyInstallStatus = KeyInstallStatus.Failure,
                keyInstallMessage = "Generate a key first."
            )
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(
                keyInstallStatus = KeyInstallStatus.Installing,
                keyInstallMessage = "Installing public key with the one-time password..."
            )
            val current = _state.value
            val password = current.password.ifBlank {
                editingId?.let { securePrefs.getPassword(it) }.orEmpty()
            }
            val server = Server(
                id = editingId ?: 0L,
                name = current.name.ifBlank { current.host },
                host = current.host.trim(),
                port = current.port.toIntOrNull() ?: 22,
                username = current.username.trim(),
                projectsFolder = current.projectsFolder,
                setupScript = current.setupScript
            )
            val result = publicKeyInstaller.install(server, password, current.publicKey)
            _state.value = _state.value.copy(
                keyInstallStatus = if (result.isSuccess) KeyInstallStatus.Success else KeyInstallStatus.Failure,
                keyInstallMessage = result.exceptionOrNull()?.message
                    ?: "Public key installed. Save the server and use key login; the password was only needed once."
            )
        }
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
                authType = if (s.privateKey.isNotBlank() || s.hasSavedPrivateKey) "key" else "password",
                projectsFolder = s.projectsFolder,
                setupScript = s.setupScript
            )
            val usesPrivateKey = s.privateKey.isNotBlank() || s.hasSavedPrivateKey
            // For edits: save credentials before DB update so the Room flow never
            // sees the server without credentials already in place.
            if (editingId != null) {
                if (s.privateKey.isNotBlank()) securePrefs.savePrivateKey(editingId!!, s.privateKey.trim())
                if (usesPrivateKey) {
                    securePrefs.deletePassword(editingId!!)
                } else if (s.password.isNotBlank()) {
                    securePrefs.savePassword(editingId!!, s.password)
                }
            }
            val savedId = if (editingId != null) {
                repo.update(server); editingId!!
            } else {
                // New server: DB generates the ID, save credentials immediately after.
                val id = repo.save(server)
                if (s.privateKey.isNotBlank()) securePrefs.savePrivateKey(id, s.privateKey.trim())
                if (!usesPrivateKey && s.password.isNotBlank()) securePrefs.savePassword(id, s.password)
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
            val privateKey = s.privateKey.ifBlank {
                editingId?.let { securePrefs.getPrivateKey(it) }.orEmpty()
            }
            val conn = sshManager.createSession(
                server = server,
                password = password.takeIf { privateKey.isBlank() },
                privateKeyPem = privateKey.takeIf { it.isNotBlank() }
            )
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
