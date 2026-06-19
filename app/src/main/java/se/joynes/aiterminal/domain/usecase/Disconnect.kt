package se.joynes.aiterminal.domain.usecase

import se.joynes.aiterminal.data.ssh.SshManager
import javax.inject.Inject

class Disconnect @Inject constructor(private val sshManager: SshManager) {
    operator fun invoke(sessionId: String) = sshManager.destroySession(sessionId)
}
