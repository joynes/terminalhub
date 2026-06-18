package se.joynes.aiterminalhub

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.joynes.aiterminalhub.domain.TerminalSessionId
import se.joynes.aiterminalhub.ui.navigation.SessionTabBar
import se.joynes.aiterminalhub.ui.screen.sessions.ProjectTabState
import se.joynes.aiterminalhub.ui.theme.AITerminalTheme

@RunWith(AndroidJUnit4::class)
class SessionTabSwipeTest {
    
    @get:Rule val composeRule = createComposeRule()


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
            AITerminalTheme {
                SessionTabBar(
                    tabs = tabs,
                    activeId = tabs.first().sessionId,
                    onSelect = {},
                    onClose = { _, _ -> },
                    onMove = { _, _ -> },
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
            AITerminalTheme {
                SessionTabBar(
                    tabs = tabs,
                    activeId = tabs.first().sessionId,
                    onSelect = {},
                    onClose = { _, _ -> },
                    onMove = { _, _ -> },
                    onAddProject = {}
                )
            }
        }
        composeRule.onNodeWithText("+").assertIsDisplayed()
    }
}
