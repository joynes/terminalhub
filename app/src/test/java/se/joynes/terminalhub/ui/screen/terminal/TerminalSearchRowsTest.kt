package se.joynes.terminalhub.ui.screen.terminal

import org.junit.Assert.*
import org.junit.Test

class TerminalSearchRowsTest {
    @Test fun hardLineBreaksNeverInventOrDuplicateMatchesInAdjacentRows() {
        val rows = listOf("Needle first", "noise", "needle second", "need", "le")
        val found = findTerminalSearchRows(" needle ", 0, rows.size, rows::get) { false }
        assertEquals(listOf(SearchMatch(0), SearchMatch(2)), found)
    }
    @Test fun softWrapFindsBoundaryMatchesButNotMatchesEntirelyOnTheNextRowTwice() {
        val rows = listOf("prefix nee", "dle rest", "wrapped prefix", "needle")
        val reads = mutableMapOf<Int, Int>()
        val found = findTerminalSearchRows("NEEDLE", 0, rows.size, { row ->
            reads[row] = (reads[row] ?: 0) + 1; rows[row]
        }) { it == 0 || it == 2 }
        assertEquals(listOf(SearchMatch(0), SearchMatch(3)), found)
        assertTrue(reads.values.all { it == 1 })
    }
    @Test fun transcriptRowsKeepTheirNegativeCoordinatesAndTheLastRowIsNotOverread() {
        val found = findTerminalSearchRows("a", -2, 1, { row ->
            check(row in -2..0); if (row == -2) "a" else "b"
        }) { true }
        assertEquals(listOf(SearchMatch(-2)), found)
    }
    @Test fun blankQueryOrEmptyRangeReadsNoTerminalRows() {
        assertTrue(findTerminalSearchRows(" \t", -3, 5, { error("Unexpected read") }, { error("Unexpected wrap") }).isEmpty())
        assertTrue(findTerminalSearchRows("x", 0, 0, { error("Unexpected read") }, { error("Unexpected wrap") }).isEmpty())
    }
}
