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
import se.joynes.aiterminalhub.ui.navigation.SessionTabBar
import se.joynes.aiterminalhub.ui.screen.sessions.ProjectTabState
import se.joynes.aiterminalhub.ui.theme.AITerminalHubTheme

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SessionTabSwipeTest {
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createAndroidComposeRule<MainActivity>()

    @Before fun setup() { hiltRule.inject() }

    private fun makeTab(id: Long, name: String) = ProjectTabState(
        projectId = id,
        projectName = name,
        sessionId = TerminalSessionId(name),
        isConnected = true
    )

    @Test
    fun sessionTabBarShowsTabs() {
        val tabs = listOf(makeTab(1L, "session-1"), makeTab(2L, "session-2"))
        composeRule.setContent {
            AITerminalHubTheme {
                SessionTabBar(
                    tabs = tabs,
                    activeId = tabs.first().sessionId,
                    onSelect = {},
                    onClose = { _, _ -> },
                    onAddProject = {}
                )
            }
        }
        composeRule.onNodeWithText("SESSION-1").assertIsDisplayed()
        composeRule.onNodeWithText("SESSION-2").assertIsDisplayed()
    }

    @Test
    fun sessionTabBarShowsAddButton() {
        val tabs = listOf(makeTab(1L, "session-1"))
        composeRule.setContent {
            AITerminalHubTheme {
                SessionTabBar(
                    tabs = tabs,
                    activeId = tabs.first().sessionId,
                    onSelect = {},
                    onClose = { _, _ -> },
                    onAddProject = {}
                )
            }
        }
        composeRule.onNodeWithText("+").assertIsDisplayed()
    }
}
