package se.joynes.terminalhub.ui.screen.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import se.joynes.terminalhub.data.repository.ServerRepository
import se.joynes.terminalhub.data.security.SecurePrefsManager
import se.joynes.terminalhub.data.ssh.SshTunnelManager
import javax.inject.Inject

@HiltViewModel
class SshTunnelViewModel @Inject constructor(
    private val manager: SshTunnelManager,
    private val servers: ServerRepository,
    private val credentials: SecurePrefsManager
) : ViewModel() {
    val tunnels = manager.tunnels
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun start(serverId: Long, localPort: String, remotePort: String) = perform {
        val server = servers.getById(serverId) ?: error("Select a server project first")
        manager.start(server, credentials.getPassword(serverId), credentials.getPrivateKey(serverId),
            localPort.toIntOrNull() ?: error("Enter a valid phone port"),
            remotePort.toIntOrNull() ?: error("Enter a valid server port"))
    }
    fun stop(port: Int) = perform { manager.stop(port) }

    private fun perform(action: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true; _error.value = null
        viewModelScope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { _error.value = error.message ?: "Tunnel operation failed" }
            finally { _busy.value = false }
        }
    }
}
