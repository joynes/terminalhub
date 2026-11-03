package se.joynes.terminalhub

import androidx.compose.ui.test.*
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

    @Before fun setup() {
        hilt.inject()
        settings.applySettingsImport(settings.settings.value, AppSettings())
    }
    private fun scrollTo(text: String) {
        compose.onNodeWithTag("settings-sections").performScrollToNode(hasText(text))
    }
    private fun expand(text: String) { scrollTo(text); compose.onNodeWithText(text).performClick() }
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
}
