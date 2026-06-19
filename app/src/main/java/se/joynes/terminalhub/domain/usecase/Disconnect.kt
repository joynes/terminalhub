package se.joynes.terminalhub.domain.usecase

import se.joynes.terminalhub.data.ssh.SshManager
import javax.inject.Inject

class Disconnect @Inject constructor(private val sshManager: SshManager) {
    operator fun invoke(sessionId: String) = sshManager.destroySession(sessionId)
}
