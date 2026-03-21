package se.joynes.aiterminalhub.data.ssh

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.connectbot.terminal.TerminalEmulator

/**
 * Wires a single [SshConnection] to a [TerminalEmulator] within a given [scope].
 * Created once per session by [se.joynes.aiterminalhub.domain.TerminalSessionManager]
 * and kept alive on tab switches so scrollback is preserved.
 */
class TerminalBackendAdapter(
    private val conn: SshConnection,
    private val emulator: TerminalEmulator,
    private val scope: CoroutineScope
) {
    fun start() {
        scope.launch {
            conn.output.collect { bytes -> emulator.writeInput(bytes) }
        }
    }
}
