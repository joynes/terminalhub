package se.joynes.terminalhub.data.settings

import org.junit.Assert.*
import org.junit.Test

class SettingsYamlTest {
    @Test fun everyUserPreferenceHasAnExportFieldIncludingFindScope() {
        val settings = AppSettings(findIncludeClosedProjects = true)
        val yaml = SettingsYaml.export(settings)
        val root = org.yaml.snakeyaml.Yaml().load<Map<String, Any>>(yaml)
        val keys = (root["settings"] as Map<*, *>).keys
        val properties = AppSettings::class.java.declaredFields.filter {
            !java.lang.reflect.Modifier.isStatic(it.modifiers) && !it.isSynthetic
        }.map { it.name }.toSet() - "backgroundSshRecommendationHandled"
        assertEquals(properties, keys)
        assertEquals(settings, SettingsYaml.preview(yaml, AppSettings()).next)
        assertTrue(runCatching { SettingsYaml.preview(file("  findIncludeClosedProjects: 1"), settings) }.isFailure)
    }
    @Test fun tabAppearanceRoundTripsWithDescriptionsAndPartialImportPreservesOtherSizes() {
        val settings = AppSettings(projectTabWidth = 110, projectTabHeight = 42, projectTabTextSize = 16,
            terminalTabWidth = 180, terminalTabHeight = 36, terminalTabTextSize = 14)
        assertEquals(settings, SettingsYaml.preview(SettingsYaml.export(settings), AppSettings()).next)
        val preview = SettingsYaml.preview(file("  projectTabWidth: 90"), settings)
        assertEquals(settings.copy(projectTabWidth = 90), preview.next)
        assertEquals(listOf("projectTabWidth"), preview.changes.map { it.key })
    }

    @Test fun tabAppearanceRejectsOutOfRangeAndWrongTypesWithoutApplyingAnything() {
        listOf("projectTabWidth: 47", "projectTabHeight: 65", "projectTabTextSize: 7",
            "terminalTabWidth: 241", "terminalTabHeight: 0", "terminalTabTextSize: 25", "projectTabWidth: 90.5").forEach {
            assertTrue(it, runCatching { SettingsYaml.preview(file("  $it"), AppSettings()) }.isFailure)
        }
    }
    @Test fun remoteSortingRoundTripsAndInvalidSortsAreRejected() {
        val settings = AppSettings(remoteDownloadSort = "DATE", remoteDownloadSortAscending = false)
        assertEquals(settings, SettingsYaml.preview(SettingsYaml.export(settings), AppSettings()).next)
        assertTrue(runCatching { SettingsYaml.preview(file("  remoteDownloadSort: RANDOM"), settings) }.isFailure)
        assertTrue(runCatching { SettingsYaml.preview(file("  remoteDownloadSortAscending: \"false\""), settings) }.isFailure)
    }
    @Test fun checkedInCommentedExampleIsImportableWithoutChangesOrDeviceApprovals() {
        val sample = listOf(java.io.File("../docs/terminalhub-settings.example.yaml"), java.io.File("docs/terminalhub-settings.example.yaml"))
            .first { it.exists() }.readText()
        val preview = SettingsYaml.preview(sample, AppSettings())
        assertTrue(preview.changes.isEmpty())
        assertFalse(preview.enablesHistory)
        assertFalse(preview.enablesBackground)
    }
    private fun file(body: String) = "format: terminalhub-settings\nversion: 1\nsettings:\n$body\n"
    @Test fun everyPublicPreferenceRoundTripsAndNoInternalStateOrPrivateContentIsIncluded() {
        val settings = AppSettings(terminalScroll = TerminalScrollSettings.fast.copy(reverseDirection = true),
            inputHistoryEnabled = true, preferFastResume = false, executeTextInputOnSend = false,
            textInputPanelOpacity = .8f, sshKeepaliveEnabled = false, keepSshActiveInBackground = true,
            automaticallyStartBackgroundSsh = true, backgroundSshRecommendationHandled = true,
            backgroundKeepaliveProfile = BackgroundKeepaliveProfile.ULTRA_BATTERY_SAVER,
            backgroundKeepaliveScope = BackgroundKeepaliveScope.ALL_SESSIONS,
            keyBarRows = listOf(listOf("HASH", "CHAR_C"), listOf("VOICE_INPUT")),
            keyBarHighlightedKeyIds = emptySet(), keyBarHighlightIntensity = .25f)
        val text = SettingsYaml.export(settings)
        val preview = SettingsYaml.preview(text, AppSettings(backgroundSshRecommendationHandled = true))
        assertEquals(settings, preview.next)
        assertTrue(preview.enablesHistory); assertTrue(preview.enablesBackground)
        assertTrue(preview.changes.any { it.key == "terminalScroll.braking" })
        assertTrue(text.contains("#")); assertTrue(text.contains("passwords"))
        assertFalse(text.contains("servers:")); assertFalse(text.contains("globalPinnedActions:"))
        assertFalse(text.contains("backgroundSshRecommendationHandled:"))
    }
    @Test fun commentsBlockListsReorderedAndPartialEditsPreserveUnmentionedFields() {
        val current = AppSettings(terminalScroll = TerminalScrollSettings.fast, keyBarHighlightIntensity = .3f)
        val text = """
            # AI adjusted only scroll speed and the keybar
            version: 1 # compatible schema
            settings:
              terminalScroll:
                remoteSpeed: 0.75 # gentler tmux
              keyBarRows:
                - [ESC, HASH, CHAR_C]
                - [CTRL, VOICE_INPUT]
            format: terminalhub-settings
        """.trimIndent()
        val next = SettingsYaml.preview(text, current).next
        assertEquals(.75f, next.terminalScroll.remoteSpeed)
        assertEquals(current.terminalScroll.braking, next.terminalScroll.braking)
        assertEquals(.3f, next.keyBarHighlightIntensity)
        assertEquals(listOf("ESC", "HASH", "CHAR_C"), next.keyBarRows.first())
        assertEquals(2, SettingsYaml.preview(text, current).changes.size)
    }
    @Test fun wrongFilesVersionsUnknownKeysDuplicatesBadTypesAndUnsafeYamlRejectWholeImport() {
        val invalid = listOf(
            "version: 2\nsettings: {}\nservers: []", file("  mystery: true"),
            file("  preferFastResume: true\n  preferFastResume: false"), file("  preferFastResume: \"false\""),
            file("  terminalScroll: {remoteFlingLimit: 10.5}"), file("  terminalScroll: {remoteSpeed: .nan}"),
            file("  terminalScroll: {remoteSpeed: 8}"), file("  textInputPanelOpacity: 0"),
            file("  keyBarRows: [[UNKNOWN]]"), file("  keyBarRows: []"), file("  keyBarRows: [[ESC, TAB, CTRL, ALT, UP, DOWN, LEFT, RIGHT, ENTER, HOME, END, HASH, AT]]"),
            file("  backgroundKeepaliveProfile: NEVER"), file("  automaticallyStartBackgroundSsh: true"),
            file("  preferFastResume: !!java.lang.String nope"),
            file("  keyBarRows: &rows [[ESC]]\n  keyBarHighlightedKeyIds: *rows"),
            file("  inputHistoryEnabled: null"), file("  terminalScroll: {unknown: 1}"),
            "format: terminalhub-settings\nversion: 99\nsettings: {}", file("  sshKeepaliveEnabled: true") + "---\nsettings: {}",
            "#".repeat(SettingsYaml.MAX_BYTES + 1)
        )
        invalid.forEach { text -> assertTrue("Should reject $text", runCatching { SettingsYaml.preview(text, AppSettings()) }.isFailure) }
    }
    @Test fun identicalFileHasNoChangesAndEnablingOnlyAutoRequiresBackgroundApproval() {
        val base = AppSettings()
        assertTrue(SettingsYaml.preview(SettingsYaml.export(base), base).changes.isEmpty())
        val preview = SettingsYaml.preview(file("  keepSshActiveInBackground: true\n  automaticallyStartBackgroundSsh: true"), base)
        assertTrue(preview.enablesBackground)
        assertFalse(preview.enablesHistory)
    }
}
