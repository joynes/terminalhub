package se.joynes.aiterminalhub.ui.screen.terminal

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import org.connectbot.terminal.TerminalEmulator
import se.joynes.aiterminalhub.domain.TerminalSessionManager
import javax.inject.Inject

@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val manager: TerminalSessionManager
) : ViewModel() {

    val activeEmulator: StateFlow<TerminalEmulator?> = manager.activeEmulator()

    fun sendBytes(bytes: ByteArray) = manager.sendBytesToActive(bytes)
}
