package se.joynes.terminalhub.data.ssh

import com.trilead.ssh2.Session
import com.trilead.ssh2.SFTPv3Client
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
    fun `concurrent project startup creates only capacity bounded transports`() = runBlocking {
        val connector = FakeConnector(connectDelayMs = 80)
        val pool = SharedSshTransportPool(logger, connector)

        val leases = (1..20).map {
            async(Dispatchers.Default) { pool.acquire(server(), "secret", null) }
        }.awaitAll()

        assertEquals(3, connector.connectCount.get())
        assertEquals(
            listOf(4, 8, 8),
            leases.groupingBy { it.debugTransportIdentity() }.eachCount().values.sorted()
        )
    }

    @Test
    fun `twelve projects spill into a second transport before server channel limit`() = runBlocking {
        val connector = FakeConnector()
        val pool = SharedSshTransportPool(logger, connector)

        val leases = (1..12).map {
            async(Dispatchers.Default) { pool.acquire(server(), "secret", null) }
        }.awaitAll()

        assertEquals(2, pool.activeTransportCount())
        assertEquals(2, connector.connectCount.get())
        assertEquals(
            listOf(4, 8),
            leases.groupingBy { it.debugTransportIdentity() }.eachCount().values.sorted()
        )
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
    fun `two transfers reuse a project transport without consuming project capacity`() = runBlocking {
        val connector = FakeConnector()
        val pool = SharedSshTransportPool(logger, connector)
        val projects = (1..8).map { pool.acquire(server(), "secret", null) }

        val firstTransfer = pool.acquireTransfer(server(), "secret", null)
        val secondTransfer = pool.acquireTransfer(server(), "secret", null)

        assertEquals(1, connector.connectCount.get())
        assertEquals(projects.first().debugTransportIdentity(), firstTransfer.debugTransportIdentity())
        assertEquals(firstTransfer.debugTransportIdentity(), secondTransfer.debugTransportIdentity())
    }

    @Test
    fun `third concurrent transfer opens another capacity bounded transport`() = runBlocking {
        val connector = FakeConnector()
        val pool = SharedSshTransportPool(logger, connector)

        val transfers = (1..3).map { pool.acquireTransfer(server(), "secret", null) }

        assertEquals(2, connector.connectCount.get())
        assertEquals(listOf(1, 2), transfers.groupingBy { it.debugTransportIdentity() }.eachCount().values.sorted())
    }

    @Test
    fun `fifth transfer waits in queue until a running transfer finishes`() = runBlocking {
        val connector = FakeConnector()
        val pool = SharedSshTransportPool(logger, connector)
        val running = (1..4).map { pool.acquireTransfer(server(), "secret", null) }

        val queued = async(Dispatchers.Default) { pool.acquireTransfer(server(), "secret", null) }
        kotlinx.coroutines.delay(100)

        assertFalse(queued.isCompleted)
        running.first().release()
        val admitted = queued.await()
        assertTrue(queued.isCompleted)

        admitted.release()
        running.drop(1).forEach { it.release() }
    }

    @Test
    fun `transfer lease keeps transport alive after last project closes`() = runBlocking {
        val connector = FakeConnector()
        val pool = SharedSshTransportPool(logger, connector)
        val project = pool.acquire(server(), "secret", null)
        val transfer = pool.acquireTransfer(server(), "secret", null)
        val transport = connector.transports.single()

        project.release()

        assertFalse(transport.closed)
        assertEquals(1, pool.activeTransportCount())

        transfer.release()
        assertTrue(transport.closed)
        assertEquals(0, pool.activeTransportCount())
    }

    @Test
    fun `transfer leases do not reduce eight reserved project channels`() = runBlocking {
        val connector = FakeConnector()
        val pool = SharedSshTransportPool(logger, connector)
        val transfers = listOf(
            pool.acquireTransfer(server(), "secret", null),
            pool.acquireTransfer(server(), "secret", null)
        )

        val projects = (1..8).map { pool.acquire(server(), "secret", null) }

        assertEquals(1, connector.connectCount.get())
        assertEquals(
            transfers.first().debugTransportIdentity(),
            projects.last().debugTransportIdentity()
        )
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

        override fun openProjectSession(): Session {
            openSessionCount += 1
            return mock()
        }
        override fun openAuxiliarySession(): SshAuxiliarySession =
            SshAuxiliarySession(mock()) {}
        override fun openSftpSession(): SshAuxiliarySftp =
            SshAuxiliarySftp(mock<SFTPv3Client>()) {}
        override fun updateProjectIds(projectIds: Set<Long>) {
            projectUpdates += projectIds
        }
        override fun close() { closed = true }
        override fun debugIdentity(): Int = identity
    }
}
