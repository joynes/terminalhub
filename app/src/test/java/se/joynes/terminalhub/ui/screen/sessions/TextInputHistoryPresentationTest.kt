package se.joynes.terminalhub.ui.screen.sessions

import org.junit.Assert.assertEquals
import org.junit.Test

class TextInputHistoryPresentationTest {
    @Test
    fun `multiline history is presented as one readable line`() {
        assertEquals(
            "git commit -m Settings update",
            textInputHistoryPreview("git commit\n  -m   Settings update")
        )
    }

    @Test
    fun `history preview does not alter command characters`() {
        assertEquals(
            "printf '%s' value | sed 's/a/b/'",
            textInputHistoryPreview("printf '%s' value | sed 's/a/b/'")
        )
    }
}
