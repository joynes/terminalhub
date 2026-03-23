package se.joynes.aiterminalhub.ui.screen.terminal

import androidx.lifecycle.ViewModel
import com.termux.terminal.TerminalSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import se.joynes.aiterminalhub.domain.TerminalSessionManager
import javax.inject.Inject

@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val manager: TerminalSessionManager
) : ViewModel() {

    val activeSession: StateFlow<TerminalSession?> = manager.activeSession()

    fun sendBytes(bytes: ByteArray) = manager.sendBytesToActive(bytes)
}
