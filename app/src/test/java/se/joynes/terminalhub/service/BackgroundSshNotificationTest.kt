package se.joynes.terminalhub.service

import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundSshNotificationTest {
    @Test
    fun `notification describes enabled mode with no SSH sessions`() {
        assertEquals("0 active SSH connections", backgroundSshNotificationText(0))
    }

    @Test
    fun `notification uses singular active SSH count`() {
        assertEquals("1 active SSH connection", backgroundSshNotificationText(1))
    }

    @Test
    fun `notification uses plural active SSH count`() {
        assertEquals("3 active SSH connections", backgroundSshNotificationText(3))
    }
}
