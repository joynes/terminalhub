package se.joynes.terminalhub.data.ssh

import com.trilead.ssh2.*
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*
import se.joynes.terminalhub.data.logging.AppLogger
import se.joynes.terminalhub.data.model.Server
import se.joynes.terminalhub.data.security.SecurePrefsManager

class TransferReconnectTest {
    private val logger: AppLogger = mock()
    private val server = Server(id = 1, name = "Server", host = "fixture", username = "fixture")

    @Test fun `window command recovers from closed pooled transport without closing sibling project`() = runBlocking {
        val session: Session = mock()
        whenever(session.stdout).thenReturn(ByteArrayInputStream("window list".toByteArray()))
        whenever(session.stderr).thenReturn(ByteArrayInputStream(byteArrayOf()))
        whenever(session.waitForCondition(any(), any())).thenReturn(ChannelCondition.EOF or ChannelCondition.EXIT_STATUS)
        whenever(session.exitStatus).thenReturn(0)
        val broken = Transport(failure = IOException("Sorry, this connection is closed."))
        val fresh = Transport(session = session)
        val connector = Connector(broken, fresh)
        val pool = SharedSshTransportPool(logger, connector)
        val sibling = pool.acquire(server, null, null)

        val result = RemoteCommandExecutor(pool, mock<SecurePrefsManager>()).run(server, "list-windows")

        assertEquals("window list", result.output)
        assertEquals(0, result.exitCode)
        assertEquals(2, connector.connections)
        verify(session, times(1)).execCommand("list-windows")
        assertFalse(broken.closed)
        sibling.release()
        assertTrue(broken.closed)
    }

    @Test fun `upload opens a fresh SFTP channel before writing on a stale pooled transport`() = runBlocking {
        val client: SFTPv3Client = mock()
        val handle: SFTPv3FileHandle = mock()
        whenever(client.createFileTruncate(any())).thenReturn(handle)
        val broken = Transport(failure = IOException("Sorry, this connection is closed."))
        val connector = Connector(broken, Transport(client = client))
        val pool = SharedSshTransportPool(logger, connector)
        val sibling = pool.acquire(server, null, null)

        val result = ScpUploader(logger, pool).upload(server, null, null, "file.txt", 3,
            ByteArrayInputStream(byteArrayOf(1, 2, 3)), "/demo").toList()

        assertEquals(3L, result.last().bytesSent)
        assertEquals(2, connector.connections)
        verify(client, times(1)).write(eq(handle), eq(0L), any(), eq(0), eq(3))
        assertFalse(broken.closed)
        sibling.release()
    }

    @Test fun `file listing recovers without retrying an already started SFTP operation`() = runBlocking {
        val client: SFTPv3Client = mock()
        whenever(client.ls("/demo")).thenReturn(emptyList())
        val broken = Transport(failure = IOException("Sorry, this connection is closed."))
        val connector = Connector(broken, Transport(client = client))
        val pool = SharedSshTransportPool(logger, connector)
        val sibling = pool.acquire(server, null, null)

        assertTrue(ScpDownloader(logger, pool).listFiles(server, null, null, "/demo").isEmpty())

        assertEquals(2, connector.connections)
        verify(client, times(1)).ls("/demo")
        assertFalse(broken.closed)
        sibling.release()
    }

    @Test fun `failed command after opening channel is never replayed`() = runBlocking {
        val session: Session = mock()
        whenever(session.execCommand("mutating command")).thenThrow(IOException("closed during exec"))
        val transport = Transport(session = session)
        val connector = Connector(transport)
        val pool = SharedSshTransportPool(logger, connector)
        val sibling = pool.acquire(server, null, null)

        val failure = runCatching { RemoteCommandExecutor(pool, mock<SecurePrefsManager>()).run(server, "mutating command") }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertEquals(1, connector.connections)
        verify(session, times(1)).execCommand("mutating command")
        assertFalse(transport.closed)
        sibling.release()
    }

    @Test fun `failed file write is never replayed on another transport`() = runBlocking {
        val client: SFTPv3Client = mock()
        val handle: SFTPv3FileHandle = mock()
        whenever(client.createFileTruncate(any())).thenReturn(handle)
        doThrow(IOException("closed during write")).whenever(client).write(eq(handle), any(), any(), any(), any())
        val transport = Transport(client = client)
        val connector = Connector(transport)
        val pool = SharedSshTransportPool(logger, connector)
        val sibling = pool.acquire(server, null, null)

        val failure = runCatching { ScpUploader(logger, pool).upload(server, null, null, "file.txt", 3,
            ByteArrayInputStream(byteArrayOf(1, 2, 3)), "/demo").toList() }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertEquals(1, connector.connections)
        verify(client, times(1)).createFileTruncate(any())
        verify(client, never()).mv(any(), any())
        assertFalse(transport.closed)
        sibling.release()
    }

    @Test fun `two failed channel opens stop retrying and release transfer capacity`() = runBlocking {
        val broken = Transport(failure = IOException("closed"))
        val second = Transport(failure = IOException("closed again"))
        val connector = Connector(broken, second, Transport(), Transport())
        val pool = SharedSshTransportPool(logger, connector)
        val sibling = pool.acquire(server, null, null)

        val failure = runCatching { pool.acquireSftpTransfer(server, null, null) }.exceptionOrNull()

        assertEquals("closed again", failure?.message)
        assertEquals(2, connector.connections)
        assertFalse(broken.closed)
        assertTrue(second.closed)
        val leases = kotlinx.coroutines.withTimeout(2000) { (1..4).map { pool.acquireTransfer(server, null, null) } }
        leases.forEach { it.release() }
        sibling.release()
        assertEquals(0, pool.activeTransportCount())
    }

    private class Connector(vararg val transports: Transport) : SshTransportConnector {
        var connections = 0
        override fun connect(server: Server, password: String?, privateKeyPem: String?): SshTransport = transports[connections++]
    }
    private class Transport(val failure: Exception? = null, val session: Session = mock(), val client: SFTPv3Client = mock()) : SshTransport {
        var closed = false
        override fun openProjectSession(): Session = session
        override fun openAuxiliarySession(): SshAuxiliarySession {
            failure?.let { throw it }; return SshAuxiliarySession(session) {}
        }
        override fun openSftpSession(): SshAuxiliarySftp {
            failure?.let { throw it }; return SshAuxiliarySftp(client) {}
        }
        override fun updateProjectIds(projectIds: Set<Long>) {}
        override fun close() { closed = true }
        override fun debugIdentity(): Int = System.identityHashCode(this)
    }
}
