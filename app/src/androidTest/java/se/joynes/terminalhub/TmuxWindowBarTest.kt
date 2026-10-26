package se.joynes.terminalhub

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.joynes.terminalhub.data.ssh.*
import se.joynes.terminalhub.ui.screen.sessions.*
import se.joynes.terminalhub.ui.theme.TerminalHubTheme

@RunWith(AndroidJUnit4::class)
class TmuxWindowBarTest {
    @get:Rule val compose = createComposeRule()
    private val first = TmuxWindow("@1", 0, "Codex", true, 1)
    private val second = TmuxWindow("@2", 1, "Tests", false, 1)
    private fun state() = TmuxWindowsState(projectId = 1, connected = true, snapshot = TmuxWindowsSnapshot("\$1", listOf(first, second)))

    @Test fun selectingTerminalCallsOnceAndDoesNotChangeProject() {
        var selected: TmuxWindow? = null
        compose.setContent { TerminalHubTheme { TmuxWindowBar(state(), false, {}, { selected = it }, { _, _ -> }, {}, {}) } }
        compose.onNodeWithContentDescription("Terminal 1: Codex").assertIsSelected()
        compose.onNodeWithContentDescription("Terminal 2: Tests").performClick()
        compose.runOnIdle { assertEquals(second, selected) }
    }

    @Test fun newTerminalSupportsOptionalNameAndOpeningProgressDisablesDoubleTap() {
        var current by mutableStateOf(state())
        var calls = 0
        var name = ""
        compose.setContent { TerminalHubTheme { TmuxWindowBar(current, false, { calls++; name = it; current = current.copy(busy = true, operation = "Opening terminal…") }, {}, { _, _ -> }, {}, {}) } }
        compose.onNodeWithContentDescription("New terminal in project").performClick()
        compose.onNodeWithText("Name (optional)").performTextInput("Logs")
        compose.onNodeWithText("SAVE").performClick()
        compose.onNodeWithTag("project-terminals-progress").assertExists()
        compose.onNodeWithText("Opening terminal…").assertExists()
        compose.onNodeWithContentDescription("New terminal in project").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, calls); assertEquals("Logs", name) }
    }

    @Test fun longPressRenamesAndCloseRequiresExplicitConfirmation() {
        var renamed: String? = null
        var closed: TmuxWindow? = null
        compose.setContent { TerminalHubTheme { TmuxWindowBar(state(), false, {}, {}, { _, name -> renamed = name }, { closed = it }, {}) } }
        compose.onNodeWithContentDescription("Terminal 2: Tests").performTouchInput { longClick() }
        compose.onNodeWithText("Rename terminal").performClick()
        compose.onNodeWithText("Terminal name").performTextReplacement("Build logs")
        compose.onNodeWithText("SAVE").performClick()
        compose.runOnIdle { assertEquals("Build logs", renamed) }
        compose.onNodeWithContentDescription("Terminal 2: Tests").performTouchInput { longClick() }
        compose.onNodeWithText("Close terminal").performClick()
        compose.runOnIdle { assertNull(closed) }
        compose.onNodeWithText("CLOSE TERMINAL").performClick()
        compose.runOnIdle { assertEquals(second, closed) }
    }

    @Test fun lastTerminalCannotBeClosedAndMacroOrPendingInputBlocksWindowChanges() {
        var busy by mutableStateOf(false)
        var current by mutableStateOf(state().copy(snapshot = TmuxWindowsSnapshot("\$1", listOf(first))))
        compose.setContent { TerminalHubTheme { TmuxWindowBar(current, busy, {}, { fail("Must not switch while sending input") }, { _, _ -> }, {}, {}) } }
        compose.onNodeWithContentDescription("Terminal 1: Codex").performTouchInput { longClick() }
        compose.onNodeWithText("Close terminal").assertIsNotEnabled()
        compose.onNodeWithText("DONE").performClick()
        compose.runOnIdle { current = state(); busy = true }
        compose.onNodeWithContentDescription("Terminal 2: Tests").assertIsNotEnabled()
        compose.onNodeWithContentDescription("New terminal in project").assertIsNotEnabled()
        compose.onNodeWithText("Sending input — terminal switching is paused").assertIsDisplayed()
    }

    @Test fun failedRequestHasRefreshFeedbackAndDisconnectedControlsStayDisabled() {
        var refreshes = 0
        var current by mutableStateOf(state().copy(error = "Could not update project terminals."))
        compose.setContent { TerminalHubTheme { TmuxWindowBar(current, false, {}, {}, { _, _ -> }, {}, { refreshes++ }) } }
        compose.onNodeWithText("Could not update project terminals.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Refresh project terminals").performClick()
        compose.runOnIdle { assertEquals(1, refreshes); current = current.copy(connected = false) }
        compose.onNodeWithText("Reconnect to manage project terminals").assertIsDisplayed()
        compose.onNodeWithContentDescription("New terminal in project").assertIsNotEnabled()
    }
}
