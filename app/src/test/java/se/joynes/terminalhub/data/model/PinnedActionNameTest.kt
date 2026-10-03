package se.joynes.terminalhub.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PinnedActionNameTest {
    @Test fun `blank name uses first nonblank input line`() {
        assertEquals("git status", pinnedActionName("  ", "\n  git status\nmore"))
        assertEquals("My action", pinnedActionName(" My action ", "git status"))
    }

    @Test fun `long names generated from input are shortened without splitting unicode`() {
        assertEquals("😀".repeat(40) + "…", pinnedActionName("", "😀".repeat(41)))
        assertEquals("x".repeat(40), pinnedActionName("", "x".repeat(40)))
    }
}
