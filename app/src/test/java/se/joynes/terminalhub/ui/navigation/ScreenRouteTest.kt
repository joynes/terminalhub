package se.joynes.terminalhub.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenRouteTest {
    @Test
    fun `settings reconnect route requests reconnect all`() {
        assertEquals(
            "session_host?serverId=-1&projectId=-1&reconnectAll=true",
            Screen.SessionHost.createRoute(reconnectAll = true)
        )
    }
}
