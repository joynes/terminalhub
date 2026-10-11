package se.joynes.terminalhub.data.settings

import org.junit.Assert.*
import org.junit.Test
import se.joynes.terminalhub.data.export.extractTerminalScrollFromYaml

class TerminalScrollSettingsTest {
    @Test fun roundTripPreservesEveryControlAndOlderBackupsKeepExistingSettings() {
        val custom = TerminalScrollSettings(3.25f, .75f, false, 2f, .5f, 500f, 333, 4, true)
        assertEquals(custom, TerminalScrollSettings.decode(custom.encode()))
        assertEquals(custom, extractTerminalScrollFromYaml("settings:\n  terminalScroll: \"${custom.encode()}\"\nservers:"))
        assertNull(extractTerminalScrollFromYaml("settings:\n  textInputPanelOpacity: 0.5\nservers:"))
        assertNull(TerminalScrollSettings.decode("broken"))
        assertNull(TerminalScrollSettings.decode(null))
    }
    @Test fun corruptExtremeAndNonFiniteValuesAreSafeAndPresetsRemainDistinct() {
        val bad = TerminalScrollSettings(Float.NaN, Float.POSITIVE_INFINITY, true, -1f, 999f, Float.NEGATIVE_INFINITY, -4, 900)
        val normalized = bad.normalized()
        assertEquals(1.5f, normalized.terminalSpeed)
        assertEquals(1f, normalized.remoteSpeed)
        assertEquals(.25f, normalized.flingSpeed)
        assertEquals(4f, normalized.braking)
        assertEquals(10, normalized.remoteFlingLimit)
        assertEquals(20, normalized.remoteEventsPerFrame)
        assertNotEquals(TerminalScrollSettings.gentle, TerminalScrollSettings.fast)
    }
}
