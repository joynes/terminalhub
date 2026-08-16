package se.joynes.terminalhub.ui.screen.sessions

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalTextInputTest {
    @Test
    fun sendOnlyLeavesCommandWaitingInTerminal() {
        assertEquals("git status", terminalTextInputPayload("git status", executeImmediately = false))
    }

    @Test
    fun executeImmediatelyAppendsTerminalEnter() {
        assertEquals("git status\r", terminalTextInputPayload("git status", executeImmediately = true))
    }

    @Test
    fun executeImmediatelyDoesNotDuplicateExistingLineEnding() {
        assertEquals("git status\r", terminalTextInputPayload("git status\r", executeImmediately = true))
        assertEquals("git status\n", terminalTextInputPayload("git status\n", executeImmediately = true))
    }

    @Test
    fun executeImmediatelyPreservesMultilineTextAndAddsFinalEnter() {
        assertEquals("first\nsecond\r", terminalTextInputPayload("first\nsecond", executeImmediately = true))
    }

    @Test
    fun uploadedPathIsInsertedAtCurrentCursor() {
        val result = insertTextAtCursor(
            TextFieldValue("open  please", TextRange(5)),
            "~/terminalhub/demo/file.txt"
        )

        assertEquals("open ~/terminalhub/demo/file.txt please", result.text)
        assertEquals(32, result.selection.start)
        assertEquals(result.selection.start, result.selection.end)
    }

    @Test
    fun uploadedPathReplacesSelectedTextAndLeavesCursorAfterPath() {
        val result = insertTextAtCursor(
            TextFieldValue("inspect old-path now", TextRange(8, 16)),
            "/remote/new.txt"
        )

        assertEquals("inspect /remote/new.txt now", result.text)
        assertEquals("inspect /remote/new.txt".length, result.selection.start)
    }
}
