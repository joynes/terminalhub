package se.joynes.terminalhub.data.ssh

import org.junit.Assert.*
import org.junit.Test

class TmuxWindowProtocolTest {
    @Test fun `framing ignores login banners and preserves names and stable ids`() {
        val text = "login banner\nTERMINALHUB_WINDOWS_V1\u0000\$3\u0000@7\u00001\u00001\u00002\u0000Tests å😀\t\n\u0000TERMINALHUB_WINDOWS_END\u0000logout"
        val result = TmuxWindowCommands.parse(text)
        assertEquals("\$3", result.sessionId)
        assertEquals(TmuxWindow("@7", 1, "Tests å😀\t\n", true, 2), result.windows.single())
    }
    @Test fun `partial duplicated and malformed lists fail instead of replacing current tabs`() {
        val invalid = listOf("", "TERMINALHUB_WINDOWS_V1\u0000\$1\u0000@1\u00000\u00001\u00001\u0000Name",
            "TERMINALHUB_WINDOWS_V1\u0000\$1\u0000@1\u00000\u00000\u00001\u0000Name\u0000TERMINALHUB_WINDOWS_END\u0000",
            "TERMINALHUB_WINDOWS_V1\u0000\$1\u0000@1\u00000\u00001\u00001\u0000A\u0000@1\u00001\u00000\u00001\u0000B\u0000TERMINALHUB_WINDOWS_END\u0000")
        invalid.forEach { assertTrue(runCatching { TmuxWindowCommands.parse(it) }.isFailure) }
    }
}
