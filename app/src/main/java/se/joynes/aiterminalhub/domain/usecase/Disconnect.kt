package se.joynes.aiterminalhub.domain.usecase

import se.joynes.aiterminalhub.data.ssh.SshManager
import javax.inject.Inject

class Disconnect @Inject constructor(private val sshManager: SshManager) {
    operator fun invoke(sessionId: String) = sshManager.destroySession(sessionId)
}
