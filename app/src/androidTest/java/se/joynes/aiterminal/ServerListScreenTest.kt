package se.joynes.aiterminal

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.joynes.aiterminal.ui.screen.servers.ServerListScreen
import se.joynes.aiterminal.ui.theme.AITerminalTheme

@RunWith(AndroidJUnit4::class)
class ServerListScreenTest {
    
    @get:Rule val composeRule = createComposeRule()


    @Test
    fun emptyStateIsShown() {
        composeRule.setContent {
            AITerminalTheme {
                ServerListScreen({}, {}, {}, {}, {}, {}, {})
            }
        }
        composeRule.onNodeWithText("NO SERVERS CONFIGURED").assertIsDisplayed()
    }

    @Test
    fun addButtonIsVisible() {
        composeRule.setContent {
            AITerminalTheme {
                ServerListScreen({}, {}, {}, {}, {}, {}, {})
            }
        }
        composeRule.onNodeWithContentDescription("Add server").assertIsDisplayed()
    }
}
