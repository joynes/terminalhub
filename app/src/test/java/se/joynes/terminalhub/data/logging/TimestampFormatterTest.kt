package se.joynes.terminalhub.data.logging

import org.junit.Assert.assertEquals
import org.junit.Test

class TimestampFormatterTest {
    @Test
    fun `formats timestamps as UTC ISO 8601`() {
        assertEquals("1970-01-01T00:00:00.000Z", currentUtcTimestamp(0L))
    }
}
