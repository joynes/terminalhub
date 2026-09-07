package se.joynes.terminalhub.data.ssh

import com.trilead.ssh2.Session
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class SshAuxiliarySessionTest {
    @Test
    fun `closing auxiliary channel returns capacity exactly once`() {
        val session: Session = mock()
        val releases = AtomicInteger()
        val auxiliary = SshAuxiliarySession(session, releases::incrementAndGet)

        auxiliary.close()
        auxiliary.close()

        verify(session, times(1)).close()
        assertEquals(1, releases.get())
    }
}
