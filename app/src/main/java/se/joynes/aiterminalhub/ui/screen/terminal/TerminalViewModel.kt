package se.joynes.aiterminalhub.ui.screen.terminal

import androidx.lifecycle.ViewModel
import com.termux.terminal.TerminalSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import se.joynes.aiterminalhub.domain.TerminalSessionManager
import javax.inject.Inject

@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val manager: TerminalSessionManager
) : ViewModel() {

    val activeSession: StateFlow<TerminalSession?> = manager.activeSession()
    val screenUpdates: SharedFlow<TerminalSession> = manager.screenUpdates

    fun sendBytes(bytes: ByteArray) = manager.sendBytesToActive(bytes)
    fun resizeActivePty(cols: Int, rows: Int) = manager.resizeActivePty(cols, rows)
    fun isTmuxSession(session: TerminalSession?) = manager.isTmuxSession(session)
    fun handleTouchScroll(session: TerminalSession?, rowsDown: Int) = manager.handleTouchScroll(session, rowsDown)
}
