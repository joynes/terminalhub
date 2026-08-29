package se.joynes.terminalhub

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.joynes.terminalhub.domain.TerminalSessionId
import se.joynes.terminalhub.ui.navigation.SessionTabBar
import se.joynes.terminalhub.ui.screen.sessions.ProjectTabState
import se.joynes.terminalhub.ui.theme.TerminalHubTheme

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
            TerminalHubTheme {
                SessionTabBar(
                    tabs = tabs,
                    activeId = tabs.first().sessionId,
                    onSelect = {},
                    onClose = { _, _ -> },
                    onRestartTmux = {},
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
            TerminalHubTheme {
                SessionTabBar(
                    tabs = tabs,
                    activeId = tabs.first().sessionId,
                    onSelect = {},
                    onClose = { _, _ -> },
                    onRestartTmux = {},
                    onMove = { _, _ -> },
                    onAddProject = {}
                )
            }
        }
        composeRule.onNodeWithText("+").assertIsDisplayed()
    }

    @Test
    fun disconnectedTabCanBeOpened() {
        var selectedProjectId: Long? = null
        val tab = ProjectTabState(
            projectId = 42L,
            projectName = "disconnected",
            sessionId = null,
            isConnected = false
        )
        composeRule.setContent {
            TerminalHubTheme {
                SessionTabBar(
                    tabs = listOf(tab),
                    activeId = null,
                    onSelect = { selectedProjectId = it },
                    onClose = { _, _ -> },
                    onRestartTmux = {},
                    onMove = { _, _ -> },
                    onAddProject = {}
                )
            }
        }

        composeRule.onNodeWithText("DISCONNECTED").performClick()
        composeRule.runOnIdle { assertEquals(42L, selectedProjectId) }
    }

    @Test
    fun longPressShowsRestartTmuxForTmuxTab() {
        var restartedProjectId: Long? = null
        val tab = makeTab(7L, "tmux-project").copy(usesTmux = true)
        composeRule.setContent {
            TerminalHubTheme {
                SessionTabBar(
                    tabs = listOf(tab),
                    activeId = tab.sessionId,
                    onSelect = {},
                    onClose = { _, _ -> },
                    onRestartTmux = { restartedProjectId = it },
                    onMove = { _, _ -> },
                    onAddProject = {}
                )
            }
        }

        composeRule.onNodeWithText("TMUX-PROJECT").performTouchInput { longClick() }
        composeRule.onNodeWithText("Restart tmux…").performClick()

        composeRule.runOnIdle { assertEquals(7L, restartedProjectId) }
    }

    @Test
    fun longPressCanEnterDedicatedReorderMode() {
        val tabs = listOf(makeTab(1L, "first"), makeTab(2L, "second"))
        composeRule.setContent {
            TerminalHubTheme {
                SessionTabBar(
                    tabs = tabs,
                    activeId = tabs.first().sessionId,
                    onSelect = {},
                    onClose = { _, _ -> },
                    onRestartTmux = {},
                    onMove = { _, _ -> },
                    onAddProject = {}
                )
            }
        }

        composeRule.onNodeWithText("FIRST").performTouchInput { longClick() }
        composeRule.onNodeWithText("Reorder tabs").performClick()

        composeRule.onNodeWithText("DRAG A TAB TO ITS NEW POSITION").assertIsDisplayed()
        composeRule.onNodeWithText("DONE").assertIsDisplayed()
    }
}
