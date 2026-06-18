package se.joynes.aiterminalhub

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.joynes.aiterminalhub.ui.screen.status.ServerStatusScreen
import se.joynes.aiterminalhub.ui.theme.AITerminalTheme

@RunWith(AndroidJUnit4::class)
class ServerStatusScreenTest {
    
    @get:Rule val composeRule = createComposeRule()


    @Test
    fun cpuGaugeIsVisible() {
        composeRule.setContent {
            AITerminalTheme { ServerStatusScreen(serverId = 1L, onBack = {}) }
        }
        composeRule.onNodeWithText("CPU").assertIsDisplayed()
    }

    @Test
    fun ramGaugeIsVisible() {
        composeRule.setContent {
            AITerminalTheme { ServerStatusScreen(serverId = 1L, onBack = {}) }
        }
        composeRule.onNodeWithText("RAM").assertIsDisplayed()
    }

    @Test
    fun diskGaugeIsVisible() {
        composeRule.setContent {
            AITerminalTheme { ServerStatusScreen(serverId = 1L, onBack = {}) }
        }
        composeRule.onNodeWithText("DISK").assertIsDisplayed()
    }
}
