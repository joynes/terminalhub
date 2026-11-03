package se.joynes.terminalhub.data.ssh

import com.trilead.ssh2.Connection
import com.trilead.ssh2.Session
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.*
import se.joynes.terminalhub.data.logging.AppLogger
import se.joynes.terminalhub.data.model.Server

class SshPublicKeyInstallerTest {
    private val logger: AppLogger = mock()
    private val verifier: TerminalHubHostKeyVerifier = mock()
    private val installer = SshPublicKeyInstaller(logger, verifier)
    private val server = Server(id = 1, name = "Fixture", host = "fixture.invalid", username = "user")
    @Test fun invalidPasswordOrKeyNeverOpensANetworkConnection() = runBlocking(Dispatchers.IO) {
        Mockito.mockConstruction(Connection::class.java).use { connections ->
            assertTrue(installer.install(server, "", "ssh-rsa key").isFailure)
            assertTrue(installer.install(server, "password", "not a public key").isFailure)
            assertTrue(connections.constructed().isEmpty())
        }
    }
    @Test fun successfulInstallationQuotesTheWholePublicKeyAndClosesBothChannels() = runBlocking(Dispatchers.IO) {
        val session: Session = mock()
        whenever(session.stderr).thenReturn("".byteInputStream())
        whenever(session.exitStatus).thenReturn(0)
        Mockito.mockConstruction(Connection::class.java) { connection, _ ->
            whenever(connection.authenticateWithPassword("user", "password")).thenReturn(true)
            whenever(connection.openSession()).thenReturn(session)
        }.use { connections ->
            val key = "ssh-rsa fixture comment ' with dollar ${'$'}HOME"
            assertTrue(installer.install(server, "password", key).isSuccess)
            val command = argumentCaptor<String>()
            verify(session).execCommand(command.capture())
            assertTrue(command.firstValue.contains("chmod 700 ~/.ssh"))
            assertTrue(command.firstValue.contains("chmod 600 ~/.ssh/authorized_keys"))
            assertTrue(command.firstValue.contains("grep -qxF 'ssh-rsa fixture comment '\"'\"' with dollar ${'$'}HOME'"))
            verify(session).close(); verify(connections.constructed().single()).close()
        }
    }
    @Test fun remoteFailureReturnsStderrAndStillClosesResources() = runBlocking(Dispatchers.IO) {
        val session: Session = mock()
        whenever(session.stderr).thenReturn("Permission denied".byteInputStream())
        whenever(session.exitStatus).thenReturn(1)
        Mockito.mockConstruction(Connection::class.java) { connection, _ ->
            whenever(connection.authenticateWithPassword(any(), any())).thenReturn(true)
            whenever(connection.openSession()).thenReturn(session)
        }.use { connections ->
            val result = installer.install(server, "password", "ssh-rsa fixture")
            assertEquals("Permission denied", result.exceptionOrNull()!!.message)
            verify(session).close(); verify(connections.constructed().single()).close()
        }
    }
    @Test fun authenticationAndHandshakeFailuresAreReturnedWithoutLeakingTheConnection() = runBlocking(Dispatchers.IO) {
        Mockito.mockConstruction(Connection::class.java).use { connections ->
            assertEquals("SSH password authentication failed.", installer.install(server, "wrong", "ssh-rsa fixture").exceptionOrNull()!!.message)
            verify(connections.constructed().single()).close()
        }
        Mockito.mockConstruction(Connection::class.java) { connection, _ ->
            whenever(connection.connect(verifier)).thenAnswer { throw IOException("Connection refused") }
        }.use { connections ->
            assertEquals("Connection refused", installer.install(server, "password", "ssh-rsa fixture").exceptionOrNull()!!.message)
            verify(connections.constructed().single()).close()
        }
    }
}
