package se.joynes.aiterminal

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.joynes.aiterminal.ui.screen.terminal.TerminalScreen
import se.joynes.aiterminal.ui.theme.AITerminalTheme

@RunWith(AndroidJUnit4::class)
class TerminalScreenTest {
    
    @get:Rule val composeRule = createComposeRule()


    @Test
    fun specialKeyBarIsVisible() {
        composeRule.setContent {
            AITerminalTheme { TerminalScreen() }
        }
        composeRule.onNodeWithText("ESC").assertIsDisplayed()
        composeRule.onNodeWithText("TAB").assertIsDisplayed()
        composeRule.onNodeWithText("CTRL-C").assertIsDisplayed()
    }

    @Test
    fun fontSizeControlsVisible() {
        composeRule.setContent {
            AITerminalTheme { TerminalScreen() }
        }
        composeRule.onNodeWithText("A+").assertIsDisplayed()
        composeRule.onNodeWithText("A-").assertIsDisplayed()
    }
}
