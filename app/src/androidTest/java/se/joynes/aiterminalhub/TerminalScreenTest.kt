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
import se.joynes.aiterminalhub.ui.screen.terminal.TerminalScreen
import se.joynes.aiterminalhub.ui.theme.AITerminalHubTheme

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class TerminalScreenTest {
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createAndroidComposeRule<MainActivity>()

    @Before fun setup() { hiltRule.inject() }

    @Test
    fun specialKeyBarIsVisible() {
        composeRule.setContent {
            AITerminalHubTheme { TerminalScreen(sessionId = "test-session") }
        }
        composeRule.onNodeWithText("ESC").assertIsDisplayed()
        composeRule.onNodeWithText("TAB").assertIsDisplayed()
        composeRule.onNodeWithText("CTRL-C").assertIsDisplayed()
    }

    @Test
    fun fontSizeControlsVisible() {
        composeRule.setContent {
            AITerminalHubTheme { TerminalScreen(sessionId = "test-session") }
        }
        composeRule.onNodeWithText("A+").assertIsDisplayed()
        composeRule.onNodeWithText("A-").assertIsDisplayed()
    }
}
