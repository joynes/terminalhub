package se.joynes.terminalhub

import org.junit.Assert.*
import org.junit.Test
import se.joynes.terminalhub.data.ssh.SshReconnectPolicy

class SshReconnectPolicyTest {
    private val policy = SshReconnectPolicy()

    @Test
    fun `attempt 0 returns 1 second`() {
        assertEquals(1000L, policy.delayMs(0))
    }

    @Test
    fun `attempt 1 returns 2 seconds`() {
        assertEquals(2000L, policy.delayMs(1))
    }

    @Test
    fun `attempt 2 returns 4 seconds`() {
        assertEquals(4000L, policy.delayMs(2))
    }

    @Test
    fun `attempt 3 returns 8 seconds`() {
        assertEquals(8000L, policy.delayMs(3))
    }

    @Test
    fun `attempt 6 returns 64 seconds but capped at 60`() {
        assertEquals(60_000L, policy.delayMs(6))
    }

    @Test
    fun `very large attempt capped at 60 seconds`() {
        assertEquals(60_000L, policy.delayMs(100))
    }
}
