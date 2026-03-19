package se.joynes.aiterminalhub.ui.screen.servers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import se.joynes.aiterminalhub.data.model.Server
import se.joynes.aiterminalhub.data.repository.ServerRepository
import se.joynes.aiterminalhub.data.security.SecurePrefsManager
import javax.inject.Inject

data class AddEditServerState(
    val name: String = "",
    val host: String = "",
    val port: String = "22",
    val username: String = "",
    val password: String = "",
    val saved: Boolean = false
)

@HiltViewModel
class AddEditServerViewModel @Inject constructor(
    private val repo: ServerRepository,
    private val securePrefs: SecurePrefsManager
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
                password = securePrefs.getPassword(id) ?: ""
            )
        }
    }

    fun update(block: AddEditServerState.() -> AddEditServerState) {
        _state.value = _state.value.block()
    }

    fun save() {
        viewModelScope.launch {
            val s = _state.value
            val server = Server(
                id = editingId ?: 0L,
                name = s.name.ifBlank { s.host },
                host = s.host,
                port = s.port.toIntOrNull() ?: 22,
                username = s.username
            )
            val savedId = if (editingId != null) {
                repo.update(server); editingId!!
            } else {
                repo.save(server)
            }
            if (s.password.isNotBlank()) securePrefs.savePassword(savedId, s.password)
            _state.value = _state.value.copy(saved = true)
        }
    }
}
