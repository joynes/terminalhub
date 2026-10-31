package se.joynes.terminalhub.ui.screen.sessions

import org.junit.Assert.*
import org.junit.Test

class TerminalWorkPinTest {
    @Test fun marksFollowStableWindowAcrossRenameAndNeverMatchDifferentProjectsOrBlankKeys() {
        val pin = TerminalWorkPin(12, "stable-window-key", "Codex")
        assertTrue(pin.matches(12, "stable-window-key"))
        assertFalse(pin.matches(13, "stable-window-key"))
        assertFalse(pin.matches(12, "replacement-window-key"))
        assertFalse(pin.matches(12, ""))
        assertEquals(pin, TerminalWorkPin.decode(pin.encode()))
        assertTrue(pin.copy(name = "Renamed").matches(12, "stable-window-key"))
    }
    @Test fun namesWithSeparatorsAndUnicodeArePreservedAndMalformedStorageIsIgnored() {
        val pin = TerminalWorkPin(1, "stable_key", "Kod: översikt 📌\nnotes")
        assertEquals(pin, TerminalWorkPin.decode(pin.encode()))
        listOf("", "1", "1::QQ==", "-1:key:QQ==", "1:bad/key:QQ==", "1:key:not-base64!").forEach {
            assertNull(TerminalWorkPin.decode(it))
        }
    }
}
