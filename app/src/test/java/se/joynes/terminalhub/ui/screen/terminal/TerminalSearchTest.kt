package se.joynes.terminalhub.ui.screen.terminal

import org.junit.Assert.*
import org.junit.Test

class TerminalSearchTest {
    @Test fun crossesSeveralScreenWrapsButNeverRealNewline() {
        val rows = listOf(TerminalSearchRow(-3, "abc", true), TerminalSearchRow(-2, "def", true),
            TerminalSearchRow(-1, "ghi", false), TerminalSearchRow(0, "jkl", false))
        assertEquals(listOf(SearchMatch(-3)), findTerminalMatches(rows, "bcdefgh", false))
        assertTrue(findTerminalMatches(rows, "hijk", false).isEmpty())
    }
    @Test fun preservesMeaningfulSpacesAndCaseChoice() {
        val rows = listOf(TerminalSearchRow(0, " aA b ", false), TerminalSearchRow(1, "aaa", false))
        assertEquals(listOf(SearchMatch(0)), findTerminalMatches(rows, " aA ", true))
        assertTrue(findTerminalMatches(rows, "AA", true).isEmpty())
        assertEquals(2, findTerminalMatches(rows, "AA", false).size)
        assertTrue(findTerminalMatches(rows, "", false).isEmpty())
    }
}
