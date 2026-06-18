package se.joynes.aiterminalhub

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest

import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.joynes.aiterminalhub.ui.screen.servers.AddEditServerScreen
import se.joynes.aiterminalhub.ui.theme.AITerminalTheme

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AddEditServerScreenTest {
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createAndroidComposeRule<HiltTestActivity>()

    @Before fun setup() { hiltRule.inject() }

    @Test
    fun saveButtonDisabledWhenFieldsEmpty() {
        composeRule.setContent {
            AITerminalTheme { AddEditServerScreen(serverId = null, onBack = {}) }
        }
        composeRule.onNodeWithText("[ SAVE ]").assertIsNotEnabled()
    }

    @Test
    fun saveButtonEnabledWhenHostAndUserFilled() {
        composeRule.setContent {
            AITerminalTheme { AddEditServerScreen(serverId = null, onBack = {}) }
        }
        composeRule.onNodeWithText("Hostname / IP *", useUnmergedTree = true)
            .performTextInput("192.168.1.1")
        composeRule.onNodeWithText("Username *", useUnmergedTree = true)
            .performTextInput("admin")
        composeRule.onNodeWithText("[ SAVE ]").assertIsEnabled()
    }
}
