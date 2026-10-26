package se.joynes.terminalhub.data.ssh

import org.junit.Assert.*
import org.junit.Test

class TmuxWindowProtocolTest {
    @Test fun `framing ignores login banners and preserves names and stable ids`() {
        val text = "login banner\nTERMINALHUB_WINDOWS_V2\u0000\$3\u0000@7\u00001\u00001\u00002\u0000Tests å😀\t\n\u0000stable-key\u0000codex\u0000/home/user/å\u0000TERMINALHUB_WINDOWS_END\u0000logout"
        val result = TmuxWindowCommands.parse(text)
        assertEquals("\$3", result.sessionId)
        assertEquals(TmuxWindow("@7", 1, "Tests å😀\t\n", true, 2, "stable-key", "codex", "/home/user/å"), result.windows.single())
    }
    @Test fun `partial duplicated and malformed lists fail instead of replacing current tabs`() {
        fun list(vararg rows: List<String>) = (listOf(TmuxWindowCommands.START, "\$1") + rows.flatMap { it } + TmuxWindowCommands.END).joinToString("\u0000", postfix = "\u0000")
        val a = listOf("@1", "0", "1", "1", "Name", "key-one", "codex", "/work")
        val b = listOf("@2", "1", "0", "1", "Other", "key-two", "vim", "/work/src")
        val invalid = listOf("", list(a).dropLast(10), list(a.toMutableList().apply { set(2, "0") }),
            list(a, a), list(a, b.toMutableList().apply { set(5, "key-one") }),
            list(a.toMutableList().apply { set(5, "$(touch /tmp/no)") }))
        invalid.forEach { assertTrue(runCatching { TmuxWindowCommands.parse(it) }.isFailure) }
    }
}
