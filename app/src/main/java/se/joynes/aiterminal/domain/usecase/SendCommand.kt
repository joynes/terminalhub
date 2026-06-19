package se.joynes.aiterminal.domain.usecase

import se.joynes.aiterminal.data.ssh.SshManager
import javax.inject.Inject

class SendCommand @Inject constructor(private val sshManager: SshManager) {
    operator fun invoke(sessionId: String, command: String) {
        sshManager.getSession(sessionId)?.send("$command\n")
    }
}
