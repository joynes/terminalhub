package se.joynes.terminalhub.data.settings

import org.junit.Assert.*
import org.junit.Test

class KeyboardShortcutsTest {
    @Test fun defaultsDoNotStealTerminalKeysAndSuggestionsCoverEveryAction() {
        assertTrue(AppSettings().keyboardShortcuts.isEmpty())
        val bindings = KeyboardShortcuts.parse(KeyboardShortcuts.suggestions)
        assertEquals(ShortcutAction.entries.toSet(), bindings.keys)
        assertEquals(bindings.size, bindings.values.toSet().size)
    }
    @Test fun chordsRoundTripAndRejectAmbiguousOrReservedBindings() {
        listOf("CTRL+ALT+P", "SHIFT+F12", "META+ENTER", "CTRL+SLASH").forEach {
            assertEquals(it, KeyboardShortcuts.parseChord(it).encode())
        }
        listOf("P", "SHIFT+P", "ALT+TAB", "CTRL+SPACE", "CTRL+CTRL+P", "CTRL+UNKNOWN").forEach {
            assertTrue(it, runCatching { KeyboardShortcuts.parseChord(it) }.isFailure)
        }
    }
    @Test fun assignmentsCanBeChangedAndRemovedButNotDuplicated() {
        val chord = KeyboardShortcuts.parseChord("CTRL+P")
        val rows = KeyboardShortcuts.assign(emptyList(), ShortcutAction.FIND_PROJECT, chord)
        assertTrue(runCatching { KeyboardShortcuts.assign(rows, ShortcutAction.FIND_WINDOW, chord) }.isFailure)
        assertTrue(runCatching { KeyboardShortcuts.parse(rows + rows) }.isFailure)
        assertTrue(KeyboardShortcuts.assign(rows, ShortcutAction.FIND_PROJECT, null).isEmpty())
        assertEquals(rows, KeyboardShortcuts.assign(rows, ShortcutAction.FIND_PROJECT, chord))
    }
    @Test fun settingsExportImportPreservesShortcutsAndRejectsConflicts() {
        val configured = AppSettings(keyboardShortcuts = KeyboardShortcuts.suggestions)
        assertEquals(configured, SettingsYaml.preview(SettingsYaml.export(configured), AppSettings()).next)
        val header = "format: terminalhub-settings\nversion: 1\nsettings:\n"
        assertEquals(configured.keyboardShortcuts, SettingsYaml.preview(header + "  projectTabWidth: 90\n", configured).next.keyboardShortcuts)
        assertTrue(runCatching { SettingsYaml.preview(header + "  keyboardShortcuts: [NEXT_PROJECT=CTRL+P, FIND_PROJECT=CTRL+P]\n", configured) }.isFailure)
    }
    @Test fun fuzzySearchMatchesSubsequencesAndAllWordsAndRanksDirectMatchesFirst() {
        assertNotNull(fuzzyShortcutScore("trm", "TerminalHub"))
        assertNotNull(fuzzyShortcutScore("HUB prod", "TerminalHub Production"))
        assertNull(fuzzyShortcutScore("hub missing", "TerminalHub Production"))
        assertTrue(fuzzyShortcutScore("term", "Terminal")!! < fuzzyShortcutScore("term", "The remote machine")!!)
        assertEquals(0, fuzzyShortcutScore("  ", "anything"))
    }
    @Test fun navigationWrapsAndHandlesMissingActiveItem() {
        assertNull(cyclicShortcutIndex(0, -1, true))
        assertEquals(0, cyclicShortcutIndex(3, 2, true))
        assertEquals(2, cyclicShortcutIndex(3, 0, false))
        assertEquals(0, cyclicShortcutIndex(3, -1, true))
        assertEquals(2, cyclicShortcutIndex(3, -1, false))
        assertEquals(0, cyclicShortcutIndex(1, 0, true))
    }
}
