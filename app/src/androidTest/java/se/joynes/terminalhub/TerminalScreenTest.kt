package se.joynes.terminalhub

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.joynes.terminalhub.ui.screen.terminal.TerminalScreen
import se.joynes.terminalhub.ui.theme.TerminalHubTheme

@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class TerminalScreenTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createAndroidComposeRule<HiltTestActivity>()
    @Before fun setup() { hilt.inject() }


    @Test
    fun specialKeyBarIsVisible() {
        composeRule.setContent {
            TerminalHubTheme { TerminalScreen() }
        }
        composeRule.onNodeWithText("ESC").assertIsDisplayed()
        composeRule.onNodeWithText("⇥").assertIsDisplayed()
        composeRule.onNodeWithText("CTRL").assertIsDisplayed()
    }

    @Test
    fun fontSizeControlsVisible() {
        composeRule.setContent {
            TerminalHubTheme { TerminalScreen() }
        }
        composeRule.onNodeWithText("A+").assertIsDisplayed()
        composeRule.onNodeWithText("A-").assertIsDisplayed()
    }
}
