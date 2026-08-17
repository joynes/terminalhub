package se.joynes.terminalhub.ui.screen.sessions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingTextInputLayoutTest {

    @Test
    fun `panel bottom stays above keyboard and key bar`() {
        val layout = calculateFloatingPanelVerticalLayout(
            containerHeightPx = 1_600f,
            imeBottomPx = 650f,
            bottomAvoidancePx = 150f,
            panelHeightPx = 360f,
            minPanelTopPx = 24f,
            panelBottomGapPx = 16f
        )

        assertEquals(800f, layout.availableBottomPx)
        assertEquals(424f, layout.anchoredPanelTopPx)
        assertTrue(layout.anchoredPanelTopPx + 360f <= layout.availableBottomPx)
    }

    @Test
    fun `additional configurable key bar rows move panel upward`() {
        val twoRows = calculateFloatingPanelVerticalLayout(
            containerHeightPx = 1_600f,
            imeBottomPx = 650f,
            bottomAvoidancePx = 150f,
            panelHeightPx = 360f,
            minPanelTopPx = 24f,
            panelBottomGapPx = 16f
        )
        val fourRows = calculateFloatingPanelVerticalLayout(
            containerHeightPx = 1_600f,
            imeBottomPx = 650f,
            bottomAvoidancePx = 300f,
            panelHeightPx = 360f,
            minPanelTopPx = 24f,
            panelBottomGapPx = 16f
        )

        assertEquals(150f, twoRows.anchoredPanelTopPx - fourRows.anchoredPanelTopPx)
    }

    @Test
    fun `small available area clamps panel to minimum top`() {
        val layout = calculateFloatingPanelVerticalLayout(
            containerHeightPx = 500f,
            imeBottomPx = 300f,
            bottomAvoidancePx = 150f,
            panelHeightPx = 360f,
            minPanelTopPx = 24f,
            panelBottomGapPx = 16f
        )

        assertEquals(24f, layout.anchoredPanelTopPx)
        assertEquals(24f, layout.maxPanelTopPx)
    }
}
