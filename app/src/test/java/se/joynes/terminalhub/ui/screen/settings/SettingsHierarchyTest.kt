package se.joynes.terminalhub.ui.screen.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import se.joynes.terminalhub.data.settings.BackgroundKeepaliveProfile
import se.joynes.terminalhub.data.settings.BackgroundKeepaliveScope

class SettingsHierarchyTest {
    @Test
    fun `important sections are shown before optional and diagnostic sections`() {
        assertEquals(
            listOf(
                SettingsSectionId.CONNECTIONS,
                SettingsSectionId.TERMINAL_INPUT,
                SettingsSectionId.ADVANCED,
                SettingsSectionId.STATUS
            ),
            settingsSectionOrder
        )
    }

    @Test
    fun `everyday sections start expanded and technical sections start collapsed`() {
        assertTrue(isSettingsSectionExpandedByDefault(SettingsSectionId.CONNECTIONS))
        assertTrue(isSettingsSectionExpandedByDefault(SettingsSectionId.TERMINAL_INPUT))
        assertFalse(isSettingsSectionExpandedByDefault(SettingsSectionId.ADVANCED))
        assertFalse(isSettingsSectionExpandedByDefault(SettingsSectionId.STATUS))
    }

    @Test
    fun `advanced summaries use readable labels`() {
        assertEquals("Aggressive (30 sec)", backgroundProfileLabel(BackgroundKeepaliveProfile.AGGRESSIVE))
        assertEquals("Balanced (2 min)", backgroundProfileLabel(BackgroundKeepaliveProfile.BALANCED))
        assertEquals("Battery saver (5 min)", backgroundProfileLabel(BackgroundKeepaliveProfile.BATTERY_SAVER))
        assertEquals("Ultra battery saver (10 min)", backgroundProfileLabel(BackgroundKeepaliveProfile.ULTRA_BATTERY_SAVER))
        assertEquals("Active tab only", backgroundScopeLabel(BackgroundKeepaliveScope.ACTIVE_TAB_ONLY))
        assertEquals("All SSH sessions", backgroundScopeLabel(BackgroundKeepaliveScope.ALL_SESSIONS))
    }

    @Test
    fun `battery optimization status clearly distinguishes completed setup`() {
        assertEquals("Unrestricted", batteryOptimizationStatusLabel(exempt = true))
        assertEquals("Restricted — change recommended", batteryOptimizationStatusLabel(exempt = false))
    }
}
