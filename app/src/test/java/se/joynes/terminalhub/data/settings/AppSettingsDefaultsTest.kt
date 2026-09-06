package se.joynes.terminalhub.data.settings

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class AppSettingsDefaultsTest {
    @Test
    fun `text input executes immediately by default`() {
        assertTrue(DEFAULT_EXECUTE_TEXT_INPUT_ON_SEND)
        assertTrue(AppSettings().executeTextInputOnSend)
    }

    @Test
    fun `text input panel is fifty percent opaque by default`() {
        assertEquals(0.50f, DEFAULT_TEXT_INPUT_PANEL_OPACITY)
        assertEquals(DEFAULT_TEXT_INPUT_PANEL_OPACITY, AppSettings().textInputPanelOpacity)
    }

    @Test
    fun `text input opacity is constrained to supported slider range`() {
        assertEquals(MIN_TEXT_INPUT_PANEL_OPACITY_SETTING, normalizeTextInputPanelOpacitySetting(-1f))
        assertEquals(MAX_TEXT_INPUT_PANEL_OPACITY_SETTING, normalizeTextInputPanelOpacitySetting(2f))
        assertEquals(0.65f, normalizeTextInputPanelOpacitySetting(0.65f))
    }
}
