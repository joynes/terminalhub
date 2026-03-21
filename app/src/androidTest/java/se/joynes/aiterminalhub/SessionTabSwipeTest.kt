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
import se.joynes.aiterminalhub.domain.TerminalSessionId
import se.joynes.aiterminalhub.domain.TerminalSessionMeta
import se.joynes.aiterminalhub.ui.navigation.SessionTabBar
import se.joynes.aiterminalhub.ui.theme.AITerminalHubTheme

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SessionTabSwipeTest {
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createAndroidComposeRule<MainActivity>()

    @Before fun setup() { hiltRule.inject() }

    private fun makeMeta(name: String) = TerminalSessionMeta(
        id = TerminalSessionId(name),
        projectName = name,
        isConnected = true,
        hasUnreadOutput = false,
        previewLines = emptyList()
    )

    @Test
    fun sessionTabBarShowsTabs() {
        val sessions = listOf(makeMeta("session-1"), makeMeta("session-2"))
        composeRule.setContent {
            AITerminalHubTheme {
                SessionTabBar(
                    sessions = sessions,
                    activeId = sessions.first().id,
                    onSelect = {},
                    onClose = {},
                    onAddProject = {}
                )
            }
        }
        composeRule.onNodeWithText("SESSION-1").assertIsDisplayed()
        composeRule.onNodeWithText("SESSION-2").assertIsDisplayed()
    }

    @Test
    fun sessionTabBarShowsAddButton() {
        val sessions = listOf(makeMeta("session-1"))
        composeRule.setContent {
            AITerminalHubTheme {
                SessionTabBar(
                    sessions = sessions,
                    activeId = sessions.first().id,
                    onSelect = {},
                    onClose = {},
                    onAddProject = {}
                )
            }
        }
        composeRule.onNodeWithText("+").assertIsDisplayed()
    }
}
