package se.joynes.aiterminalhub.data.ssh

import kotlinx.coroutines.delay
import javax.inject.Inject

class SshReconnectPolicy @Inject constructor() {
    private val maxDelayMs = 60_000L
    private val baseDelayMs = 1_000L

    fun delayMs(attempt: Int): Long {
        val exp = minOf(attempt, 6)
        return minOf(baseDelayMs * (1L shl exp), maxDelayMs)
    }

    suspend fun waitForAttempt(attempt: Int) {
        delay(delayMs(attempt))
    }
}
