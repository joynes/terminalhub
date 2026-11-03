package se.joynes.terminalhub.data.ssh

import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*
import se.joynes.terminalhub.data.logging.AppLogger
import se.joynes.terminalhub.data.model.Server

class SshManagerLifecycleTest {
    @Test fun sessionsAreIndependentAndClosingOnePreservesTheOther() {
        val factory: SshConnectionFactory = mock()
        val first: SshConnection = mock { on { sessionId } doReturn "first" }
        val second: SshConnection = mock { on { sessionId } doReturn "second" }
        whenever(factory.create()).thenReturn(first, second)
        val manager = SshManager(mock<AppLogger>(), factory)
        val server = Server(name = "Fixture", host = "fixture", username = "user")
        assertTrue(manager.sessions.value.isEmpty())
        assertSame(first, manager.createSession(server, "password"))
        assertSame(second, manager.createSession(server, null, "key"))
        verify(first).connect(server, "password", null)
        verify(second).connect(server, null, "key")
        assertSame(first, manager.getSession("first"))
        assertEquals(setOf("first", "second"), manager.sessions.value.keys)
        manager.destroySession("first")
        verify(first).disconnect()
        verify(second, never()).disconnect()
        assertNull(manager.getSession("first"))
        assertSame(second, manager.getSession("second"))
        assertEquals("count=1,ids=[second]", manager.debugSnapshot())
        manager.destroySession("does-not-exist")
        assertEquals(setOf("second"), manager.sessions.value.keys)
        manager.destroySession("second")
        verify(second).disconnect()
        assertTrue(manager.sessions.value.isEmpty())
    }
}
