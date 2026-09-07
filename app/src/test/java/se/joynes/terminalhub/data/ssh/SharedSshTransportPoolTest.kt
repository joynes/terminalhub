package se.joynes.terminalhub.data.ssh

import com.trilead.ssh2.Session
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import se.joynes.terminalhub.data.logging.AppLogger
import se.joynes.terminalhub.data.model.Server

class SharedSshTransportPoolTest {
    private val logger: AppLogger = mock()

    @Test
    fun `projects with same endpoint and credentials share one authenticated transport`() = runBlocking {
        val connector = FakeConnector()
        val pool = SharedSshTransportPool(logger, connector)

        val first = pool.acquire(server(), "secret", null)
        val second = pool.acquire(server(id = 99, name = "Duplicate"), "secret", null)

        assertEquals(1, connector.connectCount.get())
        assertEquals(first.debugTransportIdentity(), second.debugTransportIdentity())
        assertEquals(1, pool.activeTransportCount())
    }

    @Test
    fun `concurrent project startup performs only one handshake`() = runBlocking {
        val connector = FakeConnector(connectDelayMs = 80)
        val pool = SharedSshTransportPool(logger, connector)

        val leases = (1..20).map {
            async(Dispatchers.Default) { pool.acquire(server(), "secret", null) }
        }.awaitAll()

        assertEquals(1, connector.connectCount.get())
        assertEquals(1, leases.map { it.debugTransportIdentity() }.distinct().size)
    }

    @Test
    fun `shared transport opens an independent SSH channel for every project`() = runBlocking {
        val connector = FakeConnector()
        val pool = SharedSshTransportPool(logger, connector)
        val first = pool.acquire(server(), "secret", null)
        val second = pool.acquire(server(), "secret", null)

        val firstChannel = first.openSession()
        val secondChannel = second.openSession()

        assertNotSame(firstChannel, secondChannel)
        assertEquals(2, connector.transports.single().openSessionCount)
        assertEquals(first.debugTransportIdentity(), second.debugTransportIdentity())
    }

    @Test
    fun `closing one project preserves transport and other project membership`() = runBlocking {
        val connector = FakeConnector()
        val pool = SharedSshTransportPool(logger, connector)
        val first = pool.acquire(server(), "secret", null).also { it.bindProject(10) }
        val second = pool.acquire(server(), "secret", null).also { it.bindProject(20) }
        val transport = connector.transports.single()

        first.release()

        assertFalse(transport.closed)
        assertEquals(setOf(20L), transport.projectUpdates.last())
        assertEquals(1, pool.activeTransportCount())

        second.release()
        assertTrue(transport.closed)
        assertEquals(0, pool.activeTransportCount())
    }

    @Test
    fun `different authentication identities never share transport`() = runBlocking {
        val connector = FakeConnector()
        val pool = SharedSshTransportPool(logger, connector)

        val passwordA = pool.acquire(server(), "first", null)
        val passwordB = pool.acquire(server(), "second", null)
        val keyA = pool.acquire(server(), null, "PRIVATE KEY A")
        val otherUser = pool.acquire(server(username = "other"), "first", null)

        assertEquals(4, connector.connectCount.get())
        assertEquals(
            4,
            listOf(passwordA, passwordB, keyA, otherUser)
                .map { it.debugTransportIdentity() }
                .distinct().size
        )
    }

    @Test
    fun `host spelling is normalized when pooling`() = runBlocking {
        val connector = FakeConnector()
        val pool = SharedSshTransportPool(logger, connector)

        val first = pool.acquire(server(host = "Example.COM."), "secret", null)
        val second = pool.acquire(server(host = " example.com "), "secret", null)

        assertEquals(first.debugTransportIdentity(), second.debugTransportIdentity())
        assertEquals(1, connector.connectCount.get())
    }

    @Test
    fun `failed handshake is removed so retry can create a fresh transport`() = runBlocking {
        val connector = FakeConnector(failFirst = true)
        val pool = SharedSshTransportPool(logger, connector)

        val firstFailure = runCatching { pool.acquire(server(), "secret", null) }.exceptionOrNull()
        val retry = pool.acquire(server(), "secret", null)

        assertTrue(firstFailure is IOException)
        assertEquals(2, connector.connectCount.get())
        assertEquals(1, pool.activeTransportCount())
        retry.release()
    }

    private fun server(
        id: Long = 1,
        name: String = "Server",
        host: String = "example.com",
        username: String = "alice"
    ) = Server(id = id, name = name, host = host, username = username)

    private class FakeConnector(
        private val connectDelayMs: Long = 0,
        private val failFirst: Boolean = false
    ) : SshTransportConnector {
        val connectCount = AtomicInteger()
        val transports = mutableListOf<FakeTransport>()

        override fun connect(server: Server, password: String?, privateKeyPem: String?): SshTransport {
            val attempt = connectCount.incrementAndGet()
            if (connectDelayMs > 0) Thread.sleep(connectDelayMs)
            if (failFirst && attempt == 1) throw IOException("simulated handshake failure")
            return FakeTransport(attempt).also { synchronized(transports) { transports += it } }
        }
    }

    private class FakeTransport(private val identity: Int) : SshTransport {
        val projectUpdates = mutableListOf<Set<Long>>()
        var closed = false
        var openSessionCount = 0

        override fun openSession(): Session {
            openSessionCount += 1
            return mock()
        }
        override fun updateProjectIds(projectIds: Set<Long>) {
            projectUpdates += projectIds
        }
        override fun close() { closed = true }
        override fun debugIdentity(): Int = identity
    }
}
