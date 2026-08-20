package se.joynes.terminalhub.ui.screen.sessions

import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class TextInputDraftStateTest {

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
