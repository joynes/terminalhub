package se.joynes.terminalhub.data.ssh

import com.trilead.ssh2.Connection
import com.trilead.ssh2.Session
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.*
import se.joynes.terminalhub.data.logging.AppLogger
import se.joynes.terminalhub.data.model.Server
import se.joynes.terminalhub.data.runtime.AppRuntimeRepository
import se.joynes.terminalhub.data.runtime.AppRuntimeState
import se.joynes.terminalhub.data.security.SshKeyGenerator
import se.joynes.terminalhub.data.settings.AppSettings
import se.joynes.terminalhub.data.settings.AppSettingsRepository

class TrileadConnectorTest {
    private val logger: AppLogger = mock()
    private val settings: AppSettingsRepository = mock<AppSettingsRepository>().also {
        whenever(it.settings).thenReturn(MutableStateFlow(AppSettings(sshKeepaliveEnabled = false)))
    }
    private val runtime: AppRuntimeRepository = mock<AppRuntimeRepository>().also {
        whenever(it.state).thenReturn(MutableStateFlow(AppRuntimeState()))
    }
    private val verifier: TerminalHubHostKeyVerifier = mock()
    private val connector = TrileadSshTransportConnector(logger, settings, runtime, verifier)
    private val server = Server(id = 1, name = "Fixture", host = "fixture.invalid", port = 2222, username = "user")

    @Test fun passwordAuthenticationUsesTheVerifiedHostAndReturnsIndependentChannels() {
        val channel: Session = mock()
        Mockito.mockConstruction(Connection::class.java) { connection, _ ->
            whenever(connection.authenticateWithPassword("user", "password")).thenReturn(true)
            whenever(connection.openSession()).thenReturn(channel)
        }.use { connections ->
            val transport = connector.connect(server, "password", null)
            try {
                val connection = connections.constructed().single()
                verify(verifier).prepareAttempt("fixture.invalid", 2222)
                verify(connection).connect(verifier, 15000, 15000)
                assertSame(channel, transport.openProjectSession())
                transport.openAuxiliarySession().use { assertSame(channel, it.session) }
                verify(channel).close()
                assertEquals(System.identityHashCode(connection), transport.debugIdentity())
            } finally { transport.close() }
            verify(connections.constructed().single()).close()
        }
    }
    @Test fun noCredentialsUsesNoneAuthenticationAndFailedAuthenticationAlwaysClosesConnection() {
        Mockito.mockConstruction(Connection::class.java) { connection, _ ->
            whenever(connection.authenticateWithNone("user")).thenReturn(true)
        }.use { connections ->
            val transport = connector.connect(server, " ", "")
            transport.close()
            verify(connections.constructed().single()).authenticateWithNone("user")
        }
        Mockito.mockConstruction(Connection::class.java).use { connections ->
            val error = runCatching { connector.connect(server, "wrong", null) }.exceptionOrNull()
            assertTrue(error is IOException); assertEquals("SSH authentication failed", error!!.message)
            verify(connections.constructed().single()).close()
        }
    }
    @Test fun aPrivateKeyTakesPrecedenceOverPasswordAndInvalidPemClosesConnection() {
        val key = SshKeyGenerator().generate("connector-fixture")
        Mockito.mockConstruction(Connection::class.java) { connection, _ ->
            whenever(connection.authenticateWithPublicKey(eq("user"), any<java.security.KeyPair>())).thenReturn(true)
        }.use { connections ->
            val transport = connector.connect(server, "unused password", key.privateKeyPem)
            transport.close()
            verify(connections.constructed().single(), never()).authenticateWithPassword(any(), any())
        }
        Mockito.mockConstruction(Connection::class.java).use { connections ->
            assertTrue(runCatching { connector.connect(server, null, "invalid PEM") }.isFailure)
            verify(connections.constructed().single()).close()
        }
    }
    @Test fun hostVerificationFailureIsPreservedInsteadOfShowingAGenericNetworkError() {
        val failure = HostKeyVerificationException(null, "Verify fingerprint")
        whenever(verifier.failureFor("fixture.invalid", 2222)).thenReturn(failure)
        Mockito.mockConstruction(Connection::class.java) { connection, _ ->
            whenever(connection.connect(verifier, 15000, 15000)).thenAnswer { throw IOException("Handshake failed") }
        }.use { connections ->
            assertSame(failure, runCatching { connector.connect(server, null, null) }.exceptionOrNull())
            verify(connections.constructed().single()).close()
            verify(connections.constructed().single(), never()).authenticateWithNone(any())
        }
    }
    @Test fun failedAuxiliaryOpenReturnsThePermitSoLaterChannelsAreNotBlocked() {
        val channel: Session = mock()
        Mockito.mockConstruction(Connection::class.java) { connection, _ ->
            whenever(connection.authenticateWithNone("user")).thenReturn(true)
            whenever(connection.openSession()).thenAnswer { throw IOException("Channel rejected") }
        }.use { connections ->
            val transport = connector.connect(server, null, null)
            try {
                repeat(3) { assertTrue(runCatching { transport.openAuxiliarySession() }.isFailure) }
                doReturn(channel).whenever(connections.constructed().single()).openSession()
                transport.openAuxiliarySession().close()
                verify(channel).close()
            } finally { transport.close() }
        }
    }
}
