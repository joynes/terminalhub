package se.joynes.terminalhub.ui.screen.sessions

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SshConnectionAttemptTest {

    @Test
    fun `connection succeeds as soon as connected becomes true`() = runTest {
        val connected = MutableStateFlow(true)
        val error = MutableStateFlow<String?>(null)

        assertEquals(
            SshConnectionAttemptResult.Connected,
            awaitSshConnectionAttempt(connected, error, 15_000)
        )
    }

    @Test
    fun `ssh error ends waiting immediately`() = runTest {
        val connected = MutableStateFlow(false)
        val error = MutableStateFlow<String?>("Network is unreachable.")

        assertEquals(
            SshConnectionAttemptResult.Failed("Network is unreachable."),
            awaitSshConnectionAttempt(connected, error, 15_000)
        )
    }

    @Test
    fun `timeout produces actionable failure instead of permanent restoring state`() = runTest {
        val connected = MutableStateFlow(false)
        val error = MutableStateFlow<String?>(null)

        assertEquals(
            SshConnectionAttemptResult.Failed(
                "Connection timed out. Check phone network, Tailscale, host, and SSH port, then try again."
            ),
            awaitSshConnectionAttempt(connected, error, 15_000)
        )
    }

    @Test
    fun `specific error wins when connection and error states update`() = runTest {
        val connected = MutableStateFlow(false)
        val error = MutableStateFlow("Host name could not be resolved.")

        assertEquals(
            SshConnectionAttemptResult.Failed("Host name could not be resolved."),
            awaitSshConnectionAttempt(connected, error, 15_000)
        )
    }
}
