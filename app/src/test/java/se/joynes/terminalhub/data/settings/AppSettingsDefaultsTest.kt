package se.joynes.terminalhub.data.settings

import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsDefaultsTest {
    @Test
    fun `text input executes immediately by default`() {
        assertTrue(DEFAULT_EXECUTE_TEXT_INPUT_ON_SEND)
        assertTrue(AppSettings().executeTextInputOnSend)
    }
}
