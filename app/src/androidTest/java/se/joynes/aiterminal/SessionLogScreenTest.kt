package se.joynes.aiterminal

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.joynes.aiterminal.ui.screen.sessionlog.SessionLogScreen
import se.joynes.aiterminal.ui.theme.AITerminalTheme

@RunWith(AndroidJUnit4::class)
class SessionLogScreenTest {
    
    @get:Rule val composeRule = createComposeRule()


    @Test
    fun emptyStateShown() {
        composeRule.setContent {
            AITerminalTheme { SessionLogScreen(onBack = {}) }
        }
        composeRule.onNodeWithText("NO SESSION LOGS").assertIsDisplayed()
    }

    @Test
    fun exportButtonVisible() {
        composeRule.setContent {
            AITerminalTheme { SessionLogScreen(onBack = {}) }
        }
        composeRule.onNodeWithText("EXP").assertIsDisplayed()
    }
}
