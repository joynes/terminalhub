package se.joynes.terminalhub.data.ssh

import org.junit.Assert.*
import org.junit.Test

class TmuxWindowLayoutTest {
    @Test fun namesOrderAndSelectionSurviveEncodingWithoutCommandsOrIds() {
        val layout = TmuxWindowLayout(listOf("Code å😀", "Logs ' \" #()", "Tests"), 2)
        assertEquals(layout, TmuxWindowLayout.decode(layout.encode()))
        assertNull(TmuxWindowLayout.decode("corrupt"))
        assertNull(TmuxWindowLayout.decode(null))
        assertTrue(runCatching { TmuxWindowLayout(emptyList(), 0).encode() }.isFailure)
        assertTrue(runCatching { TmuxWindowLayout(listOf("a"), 2).encode() }.isFailure)
    }
}
