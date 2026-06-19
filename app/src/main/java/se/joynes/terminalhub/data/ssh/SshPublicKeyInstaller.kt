package se.joynes.terminalhub.data.ssh

import com.trilead.ssh2.Connection
import com.trilead.ssh2.ExtendedServerHostKeyVerifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import se.joynes.terminalhub.data.logging.AppLogger
import se.joynes.terminalhub.data.logging.LogLevel
import se.joynes.terminalhub.data.model.Server
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SshPublicKeyInstaller @Inject constructor(
    private val logger: AppLogger
) {
    private val permissiveVerifier = object : ExtendedServerHostKeyVerifier() {
        override fun verifyServerHostKey(h: String?, p: Int, a: String?, k: ByteArray?) = true
        override fun getKnownKeyAlgorithmsForHost(h: String?, p: Int) = null
        override fun removeServerHostKey(h: String?, p: Int, a: String?, k: ByteArray?) {}
        override fun addServerHostKey(h: String?, p: Int, a: String?, k: ByteArray?) {}
    }

    suspend fun install(server: Server, password: String, publicKey: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(password.isNotBlank()) { "Password is required for first key install." }
                require(publicKey.startsWith("ssh-rsa ")) { "Generate a public key first." }

                val conn = Connection(server.host, server.port)
                try {
                    conn.connect(permissiveVerifier)
                    if (!conn.authenticateWithPassword(server.username, password)) {
                        throw IOException("SSH password authentication failed.")
                    }

                    val command = installCommand(publicKey)
                    val session = conn.openSession()
                    try {
                        session.execCommand(command)
                        val stderr = ByteArrayOutputStream()
                        session.stderr.copyTo(stderr)
                        val exitCode = session.exitStatus
                        if (exitCode != null && exitCode != 0) {
                            throw IOException(stderr.toString().ifBlank { "Remote install failed with exit $exitCode." })
                        }
                    } finally {
                        session.close()
                    }
                    logger.log(LogLevel.INFO, TAG, "Installed public key for ${server.username}@${server.host}:${server.port}")
                } finally {
                    conn.close()
                }
            }
        }

    private fun installCommand(publicKey: String): String {
        val quotedKey = shellQuote(publicKey)
        return "mkdir -p ~/.ssh && chmod 700 ~/.ssh && " +
            "touch ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys && " +
            "(grep -qxF $quotedKey ~/.ssh/authorized_keys || printf '%s\\n' $quotedKey >> ~/.ssh/authorized_keys)"
    }

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\"'\"'") + "'"

    private companion object {
        const val TAG = "SshPublicKeyInstaller"
    }
}
