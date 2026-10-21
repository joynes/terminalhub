package se.joynes.terminalhub.data.ssh

import com.trilead.ssh2.Session
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.mock
import se.joynes.terminalhub.data.model.Server

class SshTunnelManagerTest {
    private val server = Server(1, "dev", "example.test", username = "alice")
    private class Transport : SshTransport {
        var closed = false
        var forwards = 0
        var stopped = 0
        var fail = false
        override fun openProjectSession(): Session = mock()
        override fun openAuxiliarySession(): SshAuxiliarySession = error("unused")
        override fun openSftpSession(): SshAuxiliarySftp = error("unused")
        override fun openLocalForward(localPort: Int, remotePort: Int): AutoCloseable {
            if (fail) throw java.io.IOException("port occupied")
            forwards++
            return AutoCloseable { stopped++ }
        }
        override fun updateProjectIds(projectIds: Set<Long>) {}
        override fun close() { closed = true }
        override fun debugIdentity() = 1
    }
    private fun pool(transport: Transport) = SharedSshTransportPool(mock(), object : SshTransportConnector {
        override fun connect(server: Server, password: String?, privateKeyPem: String?) = transport
    })

    @Test fun tunnelsReuseFullProjectTransportAndDoNotConsumeTransferSlots() = runBlocking {
        val transport = Transport()
        val pool = pool(transport)
        val projects = (1..8).map { pool.acquire(server, "pw", null) }
        val manager = SshTunnelManager(pool)
        manager.start(server, "pw", null, 18080, 3000)
        manager.start(server, "pw", null, 18081, 8080)
        val transfers = (1..2).map { pool.acquireTransfer(server, "pw", null) }
        assertEquals(1, pool.activeTransportCount())
        manager.stop(18080); manager.stop(18080)
        assertEquals(1, transport.stopped)
        assertFalse(transport.closed)
        assertEquals("http://127.0.0.1:18081/", manager.tunnels.value.single().url)
        projects.forEach { it.release() }; transfers.forEach { it.release() }
        assertFalse(transport.closed)
        manager.stop(18081)
        assertTrue(transport.closed)
        assertTrue(manager.tunnels.value.isEmpty())
    }

    @Test fun failedForwardReleasesLeaseWithoutClosingProjectConnection() = runBlocking {
        val transport = Transport()
        val pool = pool(transport)
        val project = pool.acquire(server, null, null)
        val manager = SshTunnelManager(pool)
        transport.fail = true
        assertTrue(runCatching { manager.start(server, null, null, 18080, 3000) }.isFailure)
        assertTrue(manager.tunnels.value.isEmpty())
        assertFalse(transport.closed)
        project.release()
        assertTrue(transport.closed)
    }

    @Test fun invalidAndDuplicatePortsNeverReplaceExistingTunnel() = runBlocking {
        val transport = Transport()
        val manager = SshTunnelManager(pool(transport))
        for ((local, remote) in listOf(0 to 3000, 80 to 3000, 65536 to 3000, 18080 to 0, 18080 to 65536)) {
            assertTrue(runCatching { manager.start(server, null, null, local, remote) }.exceptionOrNull() is IllegalArgumentException)
        }
        assertEquals(0, transport.forwards)
        manager.start(server, null, null, 18080, 3000)
        assertTrue(runCatching { manager.start(server, null, null, 18080, 4000) }.isFailure)
        assertEquals(3000, manager.tunnels.value.single().remotePort)
        assertEquals(1, transport.forwards)
        manager.stop(18080)
    }
}
