package se.joynes.aiterminalhub

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit4.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.joynes.aiterminalhub.ui.navigation.SessionTabBar
import se.joynes.aiterminalhub.ui.theme.AITerminalHubTheme

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SessionTabSwipeTest {
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createAndroidComposeRule<MainActivity>()

    @Before fun setup() { hiltRule.inject() }

    @Test
    fun sessionTabBarShowsTabs() {
        val sessions = listOf("session-1", "session-2")
        composeRule.setContent {
            AITerminalHubTheme {
                SessionTabBar(
                    sessionIds = sessions,
                    selectedIndex = 0,
                    onTabSelected = {}
                )
            }
        }
        composeRule.onNodeWithText("SSH 1").assertIsDisplayed()
        composeRule.onNodeWithText("SSH 2").assertIsDisplayed()
    }

    @Test
    fun sessionTabBarShowsAddButton() {
        composeRule.setContent {
            AITerminalHubTheme {
                SessionTabBar(sessionIds = listOf("session-1"), selectedIndex = 0, onTabSelected = {})
            }
        }
        composeRule.onNodeWithText("+").assertIsDisplayed()
    }
}
