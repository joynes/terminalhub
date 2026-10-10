package se.joynes.terminalhub.ui.screen.sessions

import org.junit.Assert.*
import org.junit.Test
import se.joynes.terminalhub.data.runtime.BackgroundSshMode

class AutomaticBackgroundSshTest {
    @Test fun `saved approval only authorizes a visible connected permitted start`() {
        fun eligible(optedIn: Boolean = true, enabled: Boolean = true, visible: Boolean = true,
            permission: Boolean = true, running: Boolean = false, mode: BackgroundSshMode = BackgroundSshMode.OFF,
            connected: Int = 1) = shouldAutomaticallyStartBackgroundSsh(optedIn, enabled, visible, permission, running, mode, connected)
        assertTrue(eligible())
        assertFalse(eligible(optedIn = false))
        assertFalse(eligible(enabled = false))
        assertFalse(eligible(visible = false))
        assertFalse(eligible(permission = false))
        assertFalse(eligible(connected = 0))
        assertFalse(eligible(running = true))
        for (mode in listOf(BackgroundSshMode.STARTING, BackgroundSshMode.ACTIVE, BackgroundSshMode.STOPPING)) assertFalse(eligible(mode = mode))
    }
}
