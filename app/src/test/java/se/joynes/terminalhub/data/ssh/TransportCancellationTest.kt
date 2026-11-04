package se.joynes.terminalhub.data.ssh

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*
import se.joynes.terminalhub.data.model.Server

class TransportCancellationTest {
    private val server = Server(id = 1, name = "Fixture", host = "fixture.invalid", username = "user")
    @Test fun cancellingTheOnlyWaiterRemovesItsLeaseAndClosesTransportWhenLateConnectionReturns() = runBlocking {
        val started = CountDownLatch(1); val finish = CountDownLatch(1); val closed = CountDownLatch(1)
        val transport: SshTransport = mock()
        doAnswer { closed.countDown(); null }.whenever(transport).close()
        val connector = object : SshTransportConnector {
            override fun connect(server: Server, password: String?, privateKeyPem: String?): SshTransport {
                started.countDown(); check(finish.await(10, TimeUnit.SECONDS)); return transport
            }
        }
        val pool = SharedSshTransportPool(mock(), connector)
        val pending = launch(Dispatchers.Default) { pool.acquire(server, "fixture", null) }
        try {
            assertTrue(started.await(10, TimeUnit.SECONDS))
            pending.cancelAndJoin()
            assertEquals(0, pool.activeTransportCount())
            finish.countDown()
            assertTrue(closed.await(10, TimeUnit.SECONDS))
            verify(transport).close()
        } finally { finish.countDown(); pending.cancelAndJoin() }
    }
    @Test fun cancelledAuxiliaryChannelOpenReleasesLeaseWithoutRetryingOrReplayingCommands() = runBlocking {
        val count = AtomicInteger()
        val transport: SshTransport = mock()
        whenever(transport.openAuxiliarySession()).thenThrow(CancellationException("Transfer cancelled"))
        val pool = SharedSshTransportPool(mock(), object : SshTransportConnector {
            override fun connect(server: Server, password: String?, privateKeyPem: String?): SshTransport { count.incrementAndGet(); return transport }
        })
        val error = runCatching { pool.acquireCommandTransfer(server, "fixture", null) }.exceptionOrNull()
        assertTrue(error is CancellationException)
        assertEquals(1, count.get()); assertEquals(0, pool.activeTransportCount())
        verify(transport).close(); verify(transport).openAuxiliarySession()
        Unit
    }
}
