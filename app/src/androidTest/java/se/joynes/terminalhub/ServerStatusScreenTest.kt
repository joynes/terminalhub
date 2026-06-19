package se.joynes.terminalhub

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.joynes.terminalhub.ui.screen.status.ServerStatusScreen
import se.joynes.terminalhub.ui.theme.TerminalHubTheme

@RunWith(AndroidJUnit4::class)
class ServerStatusScreenTest {
    
    @get:Rule val composeRule = createComposeRule()


    @Test
    fun cpuGaugeIsVisible() {
        composeRule.setContent {
            TerminalHubTheme { ServerStatusScreen(serverId = 1L, onBack = {}) }
        }
        composeRule.onNodeWithText("CPU").assertIsDisplayed()
    }

    @Test
    fun ramGaugeIsVisible() {
        composeRule.setContent {
            TerminalHubTheme { ServerStatusScreen(serverId = 1L, onBack = {}) }
        }
        composeRule.onNodeWithText("RAM").assertIsDisplayed()
    }

    @Test
    fun diskGaugeIsVisible() {
        composeRule.setContent {
            TerminalHubTheme { ServerStatusScreen(serverId = 1L, onBack = {}) }
        }
        composeRule.onNodeWithText("DISK").assertIsDisplayed()
    }
}
