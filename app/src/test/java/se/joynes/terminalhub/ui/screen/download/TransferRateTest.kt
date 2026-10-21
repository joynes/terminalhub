package se.joynes.terminalhub.ui.screen.download

import org.junit.Assert.*
import org.junit.Test

class TransferRateTest {
    @Test fun resumedBytesDoNotInflateSpeed() {
        var now = 0L
        val rate = TransferRate { now }
        assertEquals(0.0, rate.sample(10000, 20000).bytesPerSecond, 0.0)
        now = 1000
        val sample = rate.sample(12000, 20000)
        assertEquals(2000.0, sample.bytesPerSecond, 0.0)
        assertEquals(4L, sample.remainingSeconds)
    }
}
