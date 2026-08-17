package se.joynes.terminalhub.ui.screen.sessions

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingTextInputOpacityTest {

    @Test
    fun `opacity accepts values inside slider range`() {
        assertEquals(0.55f, normalizeTextInputPanelOpacity(0.55f))
        assertEquals(1f, normalizeTextInputPanelOpacity(1f))
    }

    @Test
    fun `opacity cannot make input background completely invisible`() {
        assertEquals(MIN_TEXT_INPUT_PANEL_OPACITY, normalizeTextInputPanelOpacity(0f))
        assertEquals(MIN_TEXT_INPUT_PANEL_OPACITY, normalizeTextInputPanelOpacity(-1f))
    }

    @Test
    fun `opacity is capped at fully opaque`() {
        assertEquals(MAX_TEXT_INPUT_PANEL_OPACITY, normalizeTextInputPanelOpacity(1.5f))
    }
}
