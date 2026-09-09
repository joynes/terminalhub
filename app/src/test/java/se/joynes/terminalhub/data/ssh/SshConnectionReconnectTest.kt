package se.joynes.terminalhub.data.ssh

import com.trilead.ssh2.ChannelCondition
import com.trilead.ssh2.SFTPv3Client
import com.trilead.ssh2.Session
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import se.joynes.terminalhub.data.logging.AppLogger
import se.joynes.terminalhub.data.model.Server

class SshConnectionReconnectTest {
    @Test
    fun `channel failure retries automatically on a fresh transport`() = runBlocking {
        val logger: AppLogger = mock()
        val holdSessionOpen = CountDownLatch(1)
        val session: Session = mock()
        whenever(session.waitForCondition(any(), any())).thenAnswer {
            holdSessionOpen.await(2, TimeUnit.SECONDS)
            ChannelCondition.EOF
        }
        val failedTransport = FakeTransport(openFailure = IOException("transport is closed"))
        val freshTransport = FakeTransport(session = session)
        val connector = QueueConnector(listOf(failedTransport, freshTransport))
        val connection = SshConnection(logger, SharedSshTransportPool(logger, connector))
        val connected = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(2_000) { connection.connected.first { it } }
        }

        connection.connect(
            Server(id = 1, name = "Server", host = "example.com", username = "alice"),
            password = "secret"
        )
        connected.await()

        assertEquals(2, connector.connectCount)
        assertTrue(failedTransport.closed)
        assertFalse(freshTransport.closed)

        connection.disconnect()
        holdSessionOpen.countDown()
    }

    private class QueueConnector(
        private val transports: List<SshTransport>
    ) : SshTransportConnector {
        var connectCount = 0

        override fun connect(
            server: Server,
            password: String?,
            privateKeyPem: String?
        ): SshTransport = transports[connectCount++]
    }

    private class FakeTransport(
        private val openFailure: Exception? = null,
        private val session: Session = mock()
    ) : SshTransport {
        var closed = false

        override fun openProjectSession(): Session = openFailure?.let { throw it } ?: session
        override fun openAuxiliarySession() = SshAuxiliarySession(mock()) {}
        override fun openSftpSession() = SshAuxiliarySftp(mock<SFTPv3Client>()) {}
        override fun updateProjectIds(projectIds: Set<Long>) = Unit
        override fun close() { closed = true }
        override fun debugIdentity(): Int = System.identityHashCode(this)
    }
}
