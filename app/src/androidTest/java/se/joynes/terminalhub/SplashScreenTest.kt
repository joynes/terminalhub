package se.joynes.terminalhub

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.joynes.terminalhub.ui.screen.splash.SplashScreen
import se.joynes.terminalhub.ui.theme.TerminalHubTheme

@RunWith(AndroidJUnit4::class)
class SplashScreenTest {
    
    @get:Rule val composeRule = createComposeRule()


    @Test
    fun splashScreenShowsTitle() {
        composeRule.setContent {
            TerminalHubTheme { SplashScreen(onAuthSuccess = {}) }
        }
        composeRule.onNodeWithText("AI TERMINAL HUB").assertIsDisplayed()
    }

    @Test
    fun authenticateButtonIsDisplayed() {
        composeRule.setContent {
            TerminalHubTheme { SplashScreen(onAuthSuccess = {}) }
        }
        composeRule.onNodeWithText("[ AUTHENTICATE ]").assertIsDisplayed()
    }
}
