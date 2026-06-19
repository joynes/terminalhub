package se.joynes.aiterminal.domain.usecase

import se.joynes.aiterminal.data.model.Server
import se.joynes.aiterminal.data.security.SecurePrefsManager
import se.joynes.aiterminal.data.ssh.SshConnection
import se.joynes.aiterminal.data.ssh.SshManager
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
