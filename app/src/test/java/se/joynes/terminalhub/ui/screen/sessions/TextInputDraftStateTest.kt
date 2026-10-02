package se.joynes.terminalhub.ui.screen.sessions

import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Test

class TextInputDraftStateTest {

    @Test
    fun `repeated dictation preserves text and ignores stale keyboard update during recording`() {
        val first = voiceInputDraftAfterResult(TextFieldValue(), "first")
        val positioned = first.copy(text = "first end", selection = TextRange(5))
        val duringRecording = textInputDraftAfterChange(
            isInputVisible = false,
            currentDraft = positioned,
            updatedDraft = TextFieldValue("stale keyboard text")
        )
        val second = voiceInputDraftAfterResult(duringRecording, " second")

        assertEquals("first second end", second.text)
        assertEquals(TextRange(12), second.selection)
        assertEquals("first second third end", voiceInputDraftAfterResult(second, " third").text)
    }

    @Test
    fun `cancelled dictation preserves text selection and composition`() {
        val draft = TextFieldValue("existing text", TextRange(2, 5), TextRange(0, 5))
        assertEquals(draft, voiceInputDraftAfterResult(draft, null))
    }

    @Test
    fun `visible text input accepts keyboard updates`() {
        val updated = TextFieldValue("new command")

        assertEquals(
            updated,
            textInputDraftAfterChange(
                isInputVisible = true,
                currentDraft = TextFieldValue("old"),
                updatedDraft = updated
            )
        )
    }

    @Test
    fun `late IME update cannot restore command after send closes input`() {
        val clearedAfterSend = TextFieldValue()

        assertEquals(
            clearedAfterSend,
            textInputDraftAfterChange(
                isInputVisible = false,
                currentDraft = clearedAfterSend,
                updatedDraft = TextFieldValue("command that was already sent")
            )
        )
    }
}
