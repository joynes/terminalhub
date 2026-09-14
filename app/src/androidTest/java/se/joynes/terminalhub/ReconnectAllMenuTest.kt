package se.joynes.terminalhub

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.joynes.terminalhub.ui.screen.sessions.ReconnectAllMenuItem
import se.joynes.terminalhub.ui.theme.TerminalHubTheme

@RunWith(AndroidJUnit4::class)
class ReconnectAllMenuTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun reconnectAllMenuItemIsVisibleAndOneTapTriggersItOnce() {
        var reconnectCalls = 0
        composeRule.setContent {
            TerminalHubTheme {
                ReconnectAllMenuItem(onClick = { reconnectCalls++ })
            }
        }

        composeRule.onNodeWithText("Reconnect all")
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle { assertEquals(1, reconnectCalls) }
    }
}
