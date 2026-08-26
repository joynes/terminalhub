package se.joynes.terminalhub.ui.screen.sessions

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalTextInputTest {
    @Test
    fun sendOnlyPastesCommandWithoutTerminalEnter() {
        assertEquals(
            TerminalTextInputSubmission("git status", sendEnter = false, enterDelayMs = 0L),
            terminalTextInputSubmission("git status", executeImmediately = false)
        )
    }

    @Test
    fun executeImmediatelySendsTerminalEnterSeparatelyAfterPasteSettles() {
        assertEquals(
            TerminalTextInputSubmission("git status", sendEnter = true, enterDelayMs = 350L),
            terminalTextInputSubmission("git status", executeImmediately = true)
        )
    }

    @Test
    fun executeImmediatelyMovesExistingLineEndingToSeparateEnter() {
        assertEquals("git status", terminalTextInputSubmission("git status\r", true).pasteText)
        assertEquals("git status", terminalTextInputSubmission("git status\n", true).pasteText)
        assertEquals("git status", terminalTextInputSubmission("git status\r\n", true).pasteText)
    }

    @Test
    fun executeImmediatelyPreservesMultilinePasteAndSendsFinalEnterSeparately() {
        val submission = terminalTextInputSubmission("first\nsecond", executeImmediately = true)

        assertEquals("first\nsecond", submission.pasteText)
        assertEquals(true, submission.sendEnter)
    }

    @Test
    fun longInteractivePromptsGetEnoughTimeToLeavePasteMode() {
        val submission = terminalTextInputSubmission("x".repeat(2_000), executeImmediately = true)

        assertEquals(MAX_TEXT_INPUT_SUBMIT_DELAY_MS, submission.enterDelayMs)
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
