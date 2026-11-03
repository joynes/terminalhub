package se.joynes.terminalhub.data.ssh

import com.trilead.ssh2.ChannelCondition
import com.trilead.ssh2.SFTPv3Client
import com.trilead.ssh2.Session
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.timeout
import org.mockito.kotlin.*
import se.joynes.terminalhub.data.logging.AppLogger
import se.joynes.terminalhub.data.model.Server
import se.joynes.terminalhub.data.security.*

class SshConnectionLifecycleTest {
    private val server = Server(id = 1, name = "Fixture", host = "fixture.invalid", username = "user")
    private class Fixture : AutoCloseable {
        val logger: AppLogger = mock()
        val session: Session = mock()
        val auxiliary: Session = mock()
        val events = LinkedBlockingQueue<Int>()
        val sent = ByteArrayOutputStream()
        var auxiliaryClosed = 0
        var transportClosed = false
        var auxiliaryFailure: Exception? = null
        val transport = object : SshTransport {
            override fun openProjectSession() = session
            override fun openAuxiliarySession(): SshAuxiliarySession {
                auxiliaryFailure?.let { throw it }
                return SshAuxiliarySession(auxiliary) { auxiliaryClosed++ }
            }
            override fun openSftpSession() = SshAuxiliarySftp(mock<SFTPv3Client>()) {}
            override fun updateProjectIds(projectIds: Set<Long>) = Unit
            override fun close() { transportClosed = true }
            override fun debugIdentity() = 123
        }
        val connector: SshTransportConnector = mock()
        val connection: SshConnection
        init {
            whenever(connector.connect(any(), anyOrNull(), anyOrNull())).thenReturn(transport)
            whenever(session.stdin).thenReturn(sent)
            whenever(session.waitForCondition(any(), any())).thenAnswer {
                val next = events.poll(5, TimeUnit.SECONDS) ?: ChannelCondition.EOF
                if (next == -9) throw IOException("read failed")
                next
            }
            doAnswer { events.offer(ChannelCondition.EOF); null }.whenever(session).close()
            connection = SshConnection(logger, SharedSshTransportPool(logger, connector))
        }
        suspend fun open(server: Server) {
            connection.bindProject(7, "Project")
            connection.connect(server, "private-credential")
            withTimeout(3000) { connection.connected.first { it } }
        }
        override fun close() { connection.disconnect(); events.offer(ChannelCondition.EOF) }
    }
    @Test fun terminalReadsOutputAndStderrThenReportsEofWithoutReplayingInput() = runBlocking {
        Fixture().use { f ->
            whenever(f.session.stdout).thenReturn("output å😀".byteInputStream())
            whenever(f.session.stderr).thenReturn("warning".byteInputStream())
            whenever(f.session.exitStatus).thenReturn(0)
            f.open(server)
            f.events.offer(ChannelCondition.STDOUT_DATA or ChannelCondition.STDERR_DATA)
            val output = withTimeout(3000) { f.connection.output.first() }
            assertEquals("output å😀", output.toString(Charsets.UTF_8))
            assertEquals(1L, withTimeout(3000) { f.connection.outputEvents.first { it > 0 } })
            f.events.offer(ChannelCondition.EOF or ChannelCondition.EXIT_STATUS or ChannelCondition.CLOSED)
            withTimeout(3000) { f.connection.connected.first { !it } }
            assertTrue(f.connection.debugSnapshot().contains("read-loop-eof:EOF|EXIT_STATUS|CLOSED"))
            assertEquals(0, f.sent.size())
        }
    }
    @Test fun writesAreOrderedPtyIsResizedAndQuiescenceWaitsWithoutLoggingTypedSecrets() = runBlocking {
        Fixture().use { f ->
            f.open(server)
            f.connection.send("private-terminal-input")
            f.connection.sendBytes(byteArrayOf(13))
            f.connection.awaitPendingWrites()
            assertEquals("private-terminal-input\r", f.sent.toString("UTF-8"))
            f.connection.resizePty(0, 24); f.connection.resizePty(80, -1)
            f.connection.resizePty(132, 40)
            verify(f.session, timeout(2000)).resizePTY(132, 40, 0, 0)
            verify(f.session, never()).resizePTY(eq(0), any(), any(), any())
            assertTrue(f.connection.awaitOutputQuiescence(quietMs = 10, timeoutMs = 500))
            assertTrue(f.connection.awaitTransportQuiescence(quietMs = 10, timeoutMs = 500))
            assertFalse(f.connection.awaitOutputQuiescence(requireNewOutput = true, quietMs = 10, timeoutMs = 30))
            val messages = argumentCaptor<String>()
            verify(f.logger, atLeastOnce()).log(any(), any(), messages.capture(), anyOrNull())
            assertTrue(messages.allValues.none { it.contains("private-terminal-input") || it.contains("private-credential") })
            assertTrue(f.connection.debugSnapshot().contains("projectId=7,projectName=Project"))
        }
    }
    @Test fun silentExecQuotesShellArgumentsDrainsBothStreamsAndReleasesOnlyItsOwnChannel() = runBlocking {
        Fixture().use { f ->
            whenever(f.auxiliary.stdout).thenReturn("setup result".byteInputStream())
            whenever(f.auxiliary.stderr).thenReturn("setup warning".byteInputStream())
            whenever(f.auxiliary.waitForCondition(any(), any())).thenReturn(ChannelCondition.EXIT_STATUS)
            whenever(f.auxiliary.exitStatus).thenReturn(3)
            f.open(server)
            assertEquals("setup result", f.connection.runSilent("printf 'hello'"))
            verify(f.auxiliary).execCommand("bash -lc 'printf '\\''hello'\\''' ".trimEnd())
            assertEquals(1, f.auxiliaryClosed)
            assertTrue(f.connection.connected.value)
            assertFalse(f.transportClosed)
        }
    }
    @Test fun silentChannelFailureDoesNotDisconnectTheTerminalAndDisconnectedCallsReturnEmpty() = runBlocking {
        Fixture().use { f ->
            assertEquals("", f.connection.runSilent("pwd"))
            f.open(server); f.auxiliaryFailure = IOException("No channel")
            assertEquals("", f.connection.runSilent("pwd"))
            assertTrue(f.connection.connected.value)
            f.connection.disconnect()
            assertFalse(f.connection.awaitOutputQuiescence(quietMs = 10, timeoutMs = 100))
            assertFalse(f.connection.awaitTransportQuiescence(quietMs = 10, timeoutMs = 100))
        }
    }
    @Test fun readFailureMarksTheConnectionClosedAndKeepsADiagnosticReason() = runBlocking {
        Fixture().use { f ->
            f.open(server); f.events.offer(-9)
            withTimeout(3000) { f.connection.connected.first { !it } }
            assertTrue(f.connection.debugSnapshot().contains("read-loop-exception:IOException"))
        }
    }
    @Test fun connectionFailuresExplainDnsTimeoutAuthenticationRefusalAndNetworkErrors() = runBlocking {
        val cases = listOf(
            UnknownHostException("unknown") to "Host name could not be resolved",
            SocketTimeoutException("timeout") to "Connection timed out",
            IOException("Authentication rejected") to "SSH authentication failed",
            IOException("Connection refused") to "Connection refused. SSH is not listening",
            IOException("No route to host") to "No route to host. Check",
            IOException("Network is unreachable") to "Network is unreachable. Check",
            IOException("custom reason") to "custom reason",
            IOException() to "SSH connection failed.",
            IllegalStateException("outer", UnknownHostException("inner")) to "Host name could not be resolved"
        )
        for ((error, expected) in cases) {
            val logger: AppLogger = mock(); val connector: SshTransportConnector = mock()
            whenever(connector.connect(any(), anyOrNull(), anyOrNull())).thenAnswer { throw error }
            val connection = SshConnection(logger, SharedSshTransportPool(logger, connector))
            connection.connect(server, null)
            val message = withTimeout(3000) { connection.lastErrorMessage.first { it != null } }!!
            assertTrue("Expected $expected in $message", message.contains(expected))
            assertFalse(connection.connected.value)
            connection.disconnect()
        }
    }
    @Test fun rejectedHostKeyRemainsAvailableForExplicitTrustAndNeverOpensAShell() = runBlocking {
        val challenge = HostKeyChallenge(SshEndpoint("fixture.invalid", 22), HostKeyChallengeKind.UNKNOWN,
            "ssh-ed25519", byteArrayOf(1, 2), "SHA256:fixture")
        val logger: AppLogger = mock(); val connector: SshTransportConnector = mock()
        whenever(connector.connect(any(), anyOrNull(), anyOrNull())).thenAnswer { throw HostKeyVerificationException(challenge, "Verify host key") }
        val connection = SshConnection(logger, SharedSshTransportPool(logger, connector))
        connection.connect(server, null)
        assertEquals(challenge, withTimeout(3000) { connection.hostKeyChallenge.first { it != null } })
        assertEquals("Verify host key", withTimeout(3000) { connection.lastErrorMessage.first { it != null } })
        assertFalse(connection.connected.value)
        connection.disconnect()
    }
}
