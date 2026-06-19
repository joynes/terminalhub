package se.joynes.terminalhub.ui.screen.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import se.joynes.terminalhub.data.model.ServerStatus
import javax.inject.Inject

@HiltViewModel
class ServerStatusViewModel @Inject constructor() : ViewModel() {
    private val _status = MutableStateFlow<ServerStatus?>(null)
    val status: StateFlow<ServerStatus?> = _status.asStateFlow()

    fun startPolling(serverId: Long) {
        // In a full implementation, this would obtain the JSch session from SshManager
        // and use ServerStatusPoller.pollStatus()
        // For now, emits a placeholder status
        MutableStateFlow(ServerStatus(serverId, 0f, 0f, 0f))
            .onEach { _status.value = it }
            .launchIn(viewModelScope)
    }
}
