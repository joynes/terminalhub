package se.joynes.terminalhub.domain.usecase

import se.joynes.terminalhub.data.model.Server
import se.joynes.terminalhub.data.security.SecurePrefsManager
import se.joynes.terminalhub.data.ssh.SshConnection
import se.joynes.terminalhub.data.ssh.SshManager
import javax.inject.Inject

class ConnectToServer @Inject constructor(
    private val sshManager: SshManager,
    private val securePrefs: SecurePrefsManager
) {
    operator fun invoke(server: Server): SshConnection {
        val password = securePrefs.getPassword(server.id)
        val privateKey = securePrefs.getPrivateKey(server.id)
        return sshManager.createSession(server, password, privateKey)
    }
}
