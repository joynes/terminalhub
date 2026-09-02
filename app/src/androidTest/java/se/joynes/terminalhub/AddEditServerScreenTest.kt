package se.joynes.terminalhub

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest

import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.joynes.terminalhub.ui.screen.servers.AddEditServerScreen
import se.joynes.terminalhub.ui.theme.TerminalHubTheme

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AddEditServerScreenTest {
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createAndroidComposeRule<HiltTestActivity>()

    @Before fun setup() { hiltRule.inject() }

    @Test
    fun saveButtonDisabledWhenFieldsEmpty() {
        composeRule.setContent {
            TerminalHubTheme { AddEditServerScreen(serverId = null, onBack = {}) }
        }
        composeRule.onNodeWithText("[ SAVE ]").assertIsNotEnabled()
    }

    @Test
    fun saveButtonEnabledWhenHostAndUserFilled() {
        composeRule.setContent {
            TerminalHubTheme { AddEditServerScreen(serverId = null, onBack = {}) }
        }
        composeRule.onNodeWithText("Host or IP address *", useUnmergedTree = true)
            .performTextInput("192.168.1.1")
        composeRule.onNodeWithText("SSH username *", useUnmergedTree = true)
            .performTextInput("admin")
        composeRule.onNodeWithText("[ SAVE ]").assertIsEnabled()
    }

    @Test
    fun privateKeyEntryIsHiddenUntilUserExplicitlyRequestsIt() {
        composeRule.setContent {
            TerminalHubTheme { AddEditServerScreen(serverId = null, onBack = {}) }
        }

        composeRule.onNodeWithText("[ I HAVE A PRIVATE KEY ]").assertIsDisplayed()
        composeRule.onNodeWithText("Paste an existing PEM private key to replace the local key.")
            .assertDoesNotExist()

        composeRule.onNodeWithText("[ I HAVE A PRIVATE KEY ]").performClick()
        composeRule.onNodeWithText("Paste an existing PEM private key to replace the local key.")
            .assertIsDisplayed()
    }

    @Test
    fun serverSetupGuideIsAvailableBeforeEnteringCredentials() {
        composeRule.setContent {
            TerminalHubTheme { AddEditServerScreen(serverId = null, onBack = {}) }
        }

        composeRule.onNodeWithText("[ HOW TO START A SERVER / FIND ITS IP ]").performClick()
        composeRule.onNodeWithText("START OR FIND A SERVER").assertIsDisplayed()
        composeRule.onNodeWithText("1. Start SSH on the computer you want to reach.")
            .assertIsDisplayed()
    }
}
