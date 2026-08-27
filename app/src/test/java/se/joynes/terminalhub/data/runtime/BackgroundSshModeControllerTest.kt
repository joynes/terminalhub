package se.joynes.terminalhub.data.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundSshModeControllerTest {
    @Test
    fun `connected SSH tab alone never starts service`() {
        val transition = reduceBackgroundSshMode(
            BackgroundSshMode.OFF,
            BackgroundSshEvent.SshTabConnected
        )

        assertEquals(BackgroundSshMode.OFF, transition.mode)
        assertEquals(BackgroundSshCommand.NONE, transition.command)
    }

    @Test
    fun `visible user start with permission and active tab starts service`() {
        val transition = reduceBackgroundSshMode(
            BackgroundSshMode.OFF,
            BackgroundSshEvent.UserStart(notificationPermissionGranted = true, activeSshSessionCount = 2)
        )

        assertEquals(BackgroundSshMode.STARTING, transition.mode)
        assertEquals(BackgroundSshCommand.START_SERVICE, transition.command)
    }

    @Test
    fun `user stop closes transports while leaving remote tmux policy untouched`() {
        val transition = reduceBackgroundSshMode(
            BackgroundSshMode.ACTIVE,
            BackgroundSshEvent.UserStop
        )

        assertEquals(BackgroundSshMode.STOPPING, transition.mode)
        assertEquals(BackgroundSshCommand.STOP_AND_CLOSE_TRANSPORTS, transition.command)
    }

    @Test
    fun `last SSH tab stops service without another transport close`() {
        val transition = reduceBackgroundSshMode(
            BackgroundSshMode.ACTIVE,
            BackgroundSshEvent.LastSshTabClosed
        )

        assertEquals(BackgroundSshMode.STOPPING, transition.mode)
        assertEquals(BackgroundSshCommand.STOP_SERVICE_ONLY, transition.command)
    }

    @Test
    fun `notification permission denial keeps mode off`() {
        val transition = reduceBackgroundSshMode(
            BackgroundSshMode.OFF,
            BackgroundSshEvent.UserStart(notificationPermissionGranted = false, activeSshSessionCount = 1)
        )

        assertEquals(BackgroundSshMode.OFF, transition.mode)
        assertEquals(BackgroundSshCommand.NONE, transition.command)
    }

    @Test
    fun `process recovery never automatically restarts service`() {
        val transition = reduceBackgroundSshMode(
            BackgroundSshMode.ACTIVE,
            BackgroundSshEvent.ProcessStarted
        )

        assertEquals(BackgroundSshMode.OFF, transition.mode)
        assertEquals(BackgroundSshCommand.NONE, transition.command)
    }

    @Test
    fun `start without an active SSH tab remains off`() {
        val transition = reduceBackgroundSshMode(
            BackgroundSshMode.OFF,
            BackgroundSshEvent.UserStart(notificationPermissionGranted = true, activeSshSessionCount = 0)
        )

        assertEquals(BackgroundSshMode.OFF, transition.mode)
        assertEquals(BackgroundSshCommand.NONE, transition.command)
    }
}
