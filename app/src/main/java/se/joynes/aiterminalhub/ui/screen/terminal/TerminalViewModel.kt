package se.joynes.aiterminalhub.ui.screen.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import se.joynes.aiterminalhub.data.ssh.SshManager
import javax.inject.Inject

@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val sshManager: SshManager
) : ViewModel() {
    private val _output = MutableStateFlow("")
    val output: StateFlow<String> = _output.asStateFlow()

    private var currentSessionId: String? = null

    fun attachSession(sessionId: String) {
        currentSessionId = sessionId
        viewModelScope.launch {
            sshManager.getSession(sessionId)?.output?.collect { text ->
                _output.value = text
            }
        }
    }

    fun send(data: String) {
        currentSessionId?.let { sshManager.getSession(it)?.send(data) }
    }
}
