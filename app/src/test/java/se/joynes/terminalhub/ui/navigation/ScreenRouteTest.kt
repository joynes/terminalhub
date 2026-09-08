package se.joynes.terminalhub.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenRouteTest {
    @Test
    fun `session route does not persist one-shot reconnect action`() {
        assertEquals(
            "session_host?serverId=-1&projectId=-1",
            Screen.SessionHost.createRoute()
        )
    }
}
