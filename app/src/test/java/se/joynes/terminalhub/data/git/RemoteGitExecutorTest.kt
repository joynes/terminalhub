package se.joynes.terminalhub.data.git

import com.trilead.ssh2.ChannelCondition
import com.trilead.ssh2.Session
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*
import se.joynes.terminalhub.data.model.Server
import se.joynes.terminalhub.data.logging.AppLogger
import se.joynes.terminalhub.data.security.SecurePrefsManager
import se.joynes.terminalhub.data.ssh.*

class RemoteGitExecutorTest {
    private val server = Server(id = 1, name = "fixture", host = "fixture", username = "fixture")

    private class Fixture(val stdout: ByteArray = "patch".toByteArray(), val stall: Boolean = false, val code: Int? = 0) {
        val closed = AtomicBoolean(false)
        val entered = AtomicBoolean(false)
        val session: Session = mock()
        val transport: SshTransport = mock()
        var connections = 0
        val connector = object : SshTransportConnector {
            override fun connect(server: Server, password: String?, privateKeyPem: String?): SshTransport { connections++; return transport }
        }
        val pool = SharedSshTransportPool(mock<AppLogger>(), connector)
        val executor = RemoteGitExecutor(pool, mock<SecurePrefsManager>())
        init {
            whenever(session.stdout).thenReturn(ByteArrayInputStream(stdout))
            whenever(session.stderr).thenReturn(ByteArrayInputStream(byteArrayOf()))
            whenever(session.exitStatus).thenReturn(code)
            whenever(session.waitForCondition(any(), any())).thenAnswer { ChannelCondition.EOF or ChannelCondition.CLOSED }
            doAnswer {
                entered.set(true)
                if (stall) synchronized(closed) { while (!closed.get()) (closed as java.lang.Object).wait(50) }
                null
            }.whenever(session).execCommand(any())
            doAnswer { synchronized(closed) { closed.set(true); (closed as java.lang.Object).notifyAll() }; null }.whenever(session).close()
            whenever(transport.openAuxiliarySession()).thenAnswer { SshAuxiliarySession(session) {} }
        }
    }

    @Test fun `git uses existing transport auxiliary channel and leaves terminal lease alive`() = runBlocking {
        val fixture = Fixture()
        val terminal = fixture.pool.acquire(server, null, null)
        assertEquals("patch", fixture.executor.run(server, "git diff").output)
        assertEquals(1, fixture.connections)
        assertEquals(1, fixture.pool.activeTransportCount())
        verify(fixture.transport, never()).openProjectSession()
        verify(fixture.transport, never()).close()
        assertTrue(fixture.closed.get())
        terminal.release()
        assertEquals(0, fixture.pool.activeTransportCount())
    }

    @Test fun `large diff is bounded and explicitly truncated`() = runBlocking {
        val fixture = Fixture(ByteArray(2 * 1024 * 1024) { 'a'.code.toByte() })
        val result = fixture.executor.run(server, "git diff")
        assertTrue(result.truncated)
        assertEquals(1024 * 1024, result.output.length)
        assertTrue(fixture.closed.get())
        assertEquals(0, fixture.pool.activeTransportCount())
    }

    @Test(timeout = 5000) fun `closing viewer cancels stalled exec and releases channel without disconnecting terminal`() = runBlocking {
        val fixture = Fixture(stall = true)
        val terminal = fixture.pool.acquire(server, null, null)
        val request = launch { fixture.executor.run(server, "git diff") }
        withTimeout(2000) { while (!fixture.entered.get()) delay(10) }
        request.cancelAndJoin()
        assertTrue(fixture.closed.get())
        verify(fixture.transport, never()).close()
        terminal.release()
    }

    @Test fun `missing exit status is failure rather than empty successful diff`() = runBlocking {
        val fixture = Fixture(code = null)
        try { fixture.executor.run(server, "git diff"); fail("Expected connection failure") }
        catch (expected: java.io.IOException) { assertTrue(expected.message!!.contains("before completion")) }
        assertTrue(fixture.closed.get())
        assertEquals(0, fixture.pool.activeTransportCount())
    }
}
