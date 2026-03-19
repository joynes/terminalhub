package se.joynes.aiterminalhub.domain.usecase

import se.joynes.aiterminalhub.data.model.Server
import se.joynes.aiterminalhub.data.security.SecurePrefsManager
import se.joynes.aiterminalhub.data.ssh.SshConnection
import se.joynes.aiterminalhub.data.ssh.SshManager
import javax.inject.Inject

class ConnectToServer @Inject constructor(
    private val sshManager: SshManager,
    private val securePrefs: SecurePrefsManager
) {
    operator fun invoke(server: Server): SshConnection {
        val password = securePrefs.getPassword(server.id)
        return sshManager.createSession(server, password)
    }
}
