package se.joynes.terminalhub.data.ssh

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.Session
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*
import se.joynes.terminalhub.data.logging.AppLogger

class TmuxIntegrationTest {
    private val session: Session = mock()
    private val logger: AppLogger = mock()
    private val tmux = TmuxIntegration(logger)
    private fun respond(text: String): ChannelExec {
        val channel: ChannelExec = mock()
        whenever(channel.inputStream).thenReturn(text.byteInputStream())
        whenever(session.openChannel("exec")).thenReturn(channel)
        return channel
    }
    @Test fun installedTmuxAndSessionListingAreReadAndChannelsClosed() = runBlocking {
        val first = respond("/usr/bin/tmux\n")
        assertTrue(tmux.hasTmux(session)); verify(first).disconnect()
        val second = respond("one\n\ntwo\n")
        assertEquals(listOf("one", "two"), tmux.listSessions(session)); verify(second).disconnect()
        Unit
    }
    @Test fun duplicateSessionAndMissingSessionAreNotReportedAsSuccess() = runBlocking {
        respond("duplicate session: one"); assertFalse(tmux.newSession(session, "one"))
        respond(""); assertTrue(tmux.newSession(session, "one")); assertTrue(tmux.attachSession(session, "one"))
        respond("can't find session"); assertFalse(tmux.attachSession(session, "missing"))
    }
    @Test fun connectionFailureNeverPretendsToCreateOrAttachATmuxSession() = runBlocking {
        whenever(session.openChannel("exec")).thenAnswer { throw IOException("Closed connection") }
        assertFalse(tmux.hasTmux(session)); assertTrue(tmux.listSessions(session).isEmpty())
        assertFalse(tmux.newSession(session, "one")); assertFalse(tmux.attachSession(session, "one"))
    }
    @Test fun namesAndTypedKeysAreSingleLiteralShellArguments() = runBlocking {
        val channel = respond("")
        tmux.sendKeys(session, "project ' one", "text ' ${'$'}HOME")
        verify(channel).setCommand("tmux send-keys -t 'project '\\'' one' 'text '\\'' ${'$'}HOME' Enter")
        verify(channel).disconnect()
        Unit
    }
}
