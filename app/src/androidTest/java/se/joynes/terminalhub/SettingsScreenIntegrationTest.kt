package se.joynes.terminalhub

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import se.joynes.terminalhub.data.settings.*
import se.joynes.terminalhub.ui.screen.settings.SettingsScreen
import se.joynes.terminalhub.ui.theme.TerminalHubTheme

@HiltAndroidTest
class SettingsScreenIntegrationTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<HiltTestActivity>()
    @Inject lateinit var settings: AppSettingsRepository
    @Inject lateinit var db: se.joynes.terminalhub.data.db.AppDatabase

    @Before fun setup() {
        hilt.inject()
        settings.applySettingsImport(settings.settings.value, AppSettings())
    }
    private fun scrollTo(text: String) {
        compose.onNodeWithTag("settings-sections").performScrollToNode(hasText(text))
    }
    private fun expand(text: String) { scrollTo(text); compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.OnClick) { it() } }
    private fun toggle(title: String) {
        compose.onNodeWithContentDescription(title).performScrollTo().performClick()
    }

    @Test fun connectionPreferencesPersistAndBackgroundStartRequiresConfirmation() {
        compose.setContent { TerminalHubTheme { SettingsScreen({}, {}) } }
        expand("CONNECTION & RECOVERY")
        toggle("SSH keepalive")
        compose.runOnIdle { assertFalse(settings.settings.value.sshKeepaliveEnabled) }
        toggle("Fast resume")
        compose.runOnIdle { assertFalse(settings.settings.value.preferFastResume) }
        toggle("Keep SSH active in background")
        compose.onNodeWithText("KEEP SSH ACTIVE IN BACKGROUND?").assertIsDisplayed()
        compose.onNodeWithText("CANCEL").performClick()
        compose.runOnIdle { assertFalse(settings.settings.value.keepSshActiveInBackground) }
    }

    @Test fun textInputPreferencesAreReachableAndStoredThroughScreen() {
        compose.setContent { TerminalHubTheme { SettingsScreen({}, {}) } }
        expand("TERMINAL & INPUT")
        scrollTo("Text input"); compose.onNodeWithText("Text input").performClick()
        toggle("Execute text input with Enter")
        compose.runOnIdle { assertFalse(settings.settings.value.executeTextInputOnSend) }
        compose.onNodeWithText("Send text only").assertExists()
        scrollTo("Text input"); compose.onNodeWithText("Text input").performClick()
        scrollTo("Project & terminal tabs"); compose.onNodeWithText("Project & terminal tabs").performClick()
        compose.onNodeWithText("Project tabs").assertExists()
        scrollTo("Project & terminal tabs"); compose.onNodeWithText("Project & terminal tabs").performClick()
        scrollTo("Keyboard shortcuts"); compose.onNodeWithText("Keyboard shortcuts").performClick()
        compose.onNodeWithText("Next project").assertExists()
    }

    @Test fun advancedFrequencyAndScopeUpdateWithoutStartingAService() {
        compose.setContent { TerminalHubTheme { SettingsScreen({}, {}) } }
        expand("ADVANCED CONNECTION TUNING")
        for ((label, value) in listOf("BATTERY SAVER (5 MIN)" to BackgroundKeepaliveProfile.BATTERY_SAVER,
            "AGGRESSIVE (30 SEC)" to BackgroundKeepaliveProfile.AGGRESSIVE)) {
            scrollTo(label); compose.onNodeWithText(label).performClick()
            compose.runOnIdle { assertEquals(value, settings.settings.value.backgroundKeepaliveProfile) }
        }
        scrollTo("ALL SSH SESSIONS"); compose.onNodeWithText("ALL SSH SESSIONS").performClick()
        compose.runOnIdle {
            assertEquals(BackgroundKeepaliveScope.ALL_SESSIONS, settings.settings.value.backgroundKeepaliveScope)
            assertFalse(settings.settings.value.keepSshActiveInBackground)
        }
        expand("STATUS & DIAGNOSTICS")
        compose.onNodeWithText("Tracked remote projects").assertExists()
        compose.onNodeWithText("Recovery pending").assertExists()
    }
    @Test fun historyApprovalAndDeletionUseTheRealRepositoryAndKeepPins() {
        runBlocking {
            db.textInputHistoryDao().insert(se.joynes.terminalhub.data.db.entity.TextInputHistoryEntity(projectId = 42, text = "history fixture"))
            db.pinnedActionDao().save(se.joynes.terminalhub.data.db.entity.PinnedActionEntity(name = "Keep pin", text = "pin fixture"))
        }
        try {
            compose.setContent { TerminalHubTheme { SettingsScreen({}, {}) } }
            expand("TERMINAL & INPUT"); expand("History & privacy")
            toggle("Save terminal input history")
            compose.onNodeWithText("ENABLE HISTORY").performClick()
            compose.runOnIdle { assertTrue(settings.settings.value.inputHistoryEnabled) }
            scrollTo("Delete saved input history"); compose.onNodeWithText("Delete saved input history").performClick()
            compose.onNodeWithText("DELETE HISTORY").performClick()
            compose.waitUntil(5000) { runBlocking { db.textInputHistoryDao().getRecentForProject(42).first().isEmpty() } }
            assertTrue(runBlocking { db.pinnedActionDao().all().any { it.name == "Keep pin" } })
            toggle("Save terminal input history")
            compose.runOnIdle { assertFalse(settings.settings.value.inputHistoryEnabled) }
        } finally { runBlocking { db.clearAllTables() } }
    }
    @Test fun tabSlidersAndTextOpacityPersistThroughScreenCallbacks() {
        compose.setContent { TerminalHubTheme { SettingsScreen({}, {}) } }
        expand("TERMINAL & INPUT"); expand("Text input")
        compose.onNode(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo)).performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(.55f) }
        compose.runOnIdle { assertEquals(.55f, settings.settings.value.textInputPanelOpacity) }
        expand("Text input"); expand("Project & terminal tabs")
        for ((label, value) in listOf("Project tab width" to 130f, "Project tab height" to 40f,
            "Project tab text size" to 16f, "Terminal tab width" to 150f, "Terminal tab height" to 44f, "Terminal tab text size" to 18f)) {
            compose.onNodeWithContentDescription(label).performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(value) }
        }
        compose.runOnIdle {
            assertEquals(130, settings.settings.value.projectTabWidth); assertEquals(40, settings.settings.value.projectTabHeight)
            assertEquals(16, settings.settings.value.projectTabTextSize); assertEquals(150, settings.settings.value.terminalTabWidth)
            assertEquals(44, settings.settings.value.terminalTabHeight); assertEquals(18, settings.settings.value.terminalTabTextSize)
        }
        scrollTo("Reset project tabs"); compose.onNodeWithText("Reset project tabs").performSemanticsAction(SemanticsActions.OnClick) { it() }
        scrollTo("Reset terminal tabs"); compose.onNodeWithText("Reset terminal tabs").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle { assertEquals(70, settings.settings.value.projectTabWidth); assertEquals(100, settings.settings.value.terminalTabWidth) }
    }
    @Test fun keybarHighlightStrengthAndBackupSectionCanBeConfiguredWithoutLeavingSettings() {
        compose.setContent { TerminalHubTheme { SettingsScreen({}, {}) } }
        expand("TERMINAL & INPUT"); expand("Key bar")
        compose.onNodeWithContentDescription("Key bar highlight strength").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(.3f) }
        compose.runOnIdle { assertEquals(.3f, settings.settings.value.keyBarHighlightIntensity) }
        expand("TERMINAL & INPUT"); expand("BACKUP & AI SETTINGS")
        scrollTo("EXPORT SETTINGS"); compose.onNodeWithText("EXPORT SETTINGS").assertIsEnabled()
        expand("BACKUP & AI SETTINGS")
        compose.onNodeWithText("EXPORT SETTINGS").assertDoesNotExist()
    }
    @Test fun addingRemovingHighlightingAndResettingKeysPersistsTheLayoutAndSuggestedShortcuts() {
        settings.setKeyBarRows(listOf(listOf("ESC", "CHAR_C")))
        settings.setKeyBarHighlightedKeyIds(emptySet())
        compose.setContent { TerminalHubTheme { SettingsScreen({}, {}) } }
        expand("TERMINAL & INPUT"); expand("Key bar")
        compose.onNodeWithContentDescription("Highlight ESC").performScrollTo().performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle { assertTrue("ESC" in settings.settings.value.keyBarHighlightedKeyIds) }
        compose.onNodeWithContentDescription("Remove highlight from ESC").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle { assertFalse("ESC" in settings.settings.value.keyBarHighlightedKeyIds) }
        scrollTo("ESC"); compose.onNodeWithText("ESC").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithText("REMOVE KEY").performClick()
        compose.runOnIdle { assertEquals(listOf(listOf("CHAR_C")), settings.settings.value.keyBarRows) }
        scrollTo("+ KEY"); compose.onNodeWithText("+ KEY").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithText("TAB").performScrollTo().performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle { assertEquals(listOf(listOf("CHAR_C", "TAB")), settings.settings.value.keyBarRows) }
        scrollTo("RESET DEFAULT"); compose.onNodeWithText("RESET DEFAULT").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle {
            assertEquals(KeyBarLayoutConfig.defaultRows, settings.settings.value.keyBarRows)
            assertEquals(KeyBarLayoutConfig.defaultHighlightedKeyIds, settings.settings.value.keyBarHighlightedKeyIds)
        }
        expand("Key bar"); expand("Keyboard shortcuts")
        scrollTo("Use suggested shortcuts"); compose.onNodeWithText("Use suggested shortcuts").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle { assertEquals(KeyboardShortcuts.suggestions, settings.settings.value.keyboardShortcuts) }
    }
}
