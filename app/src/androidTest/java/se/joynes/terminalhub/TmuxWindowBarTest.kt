package se.joynes.terminalhub

import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
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

    @Test fun shortcutCloseRequestRequiresConfirmationAndDoesNotCloseAutomatically() {
        var requested by mutableStateOf(false)
        var closed: TmuxWindow? = null
        compose.setContent { TerminalHubTheme { TmuxWindowBar(state(), false, {}, {}, { _, _ -> }, { closed = it }, {},
            closeRequested = requested, onCloseRequestHandled = { requested = false }) } }
        compose.runOnIdle { requested = true }
        compose.onNodeWithText("CLOSE TERMINAL").assertIsDisplayed()
        compose.runOnIdle { assertNull(closed); assertFalse(requested) }
        compose.onNodeWithText("CLOSE TERMINAL").performClick()
        compose.runOnIdle { assertEquals(first, closed) }
    }

    @Test fun longPressShowsProgramAndFolderWithoutOfferingToExecuteInteractiveInput() {
        val remembered = first.copy(key = "stable-key", currentProgram = "zsh", directory = "/work/project",
            lastProgram = TmuxProgram("codex", "/work/project/src"))
        compose.setContent { TerminalHubTheme { TmuxWindowBar(state().copy(snapshot = TmuxWindowsSnapshot("\$1", listOf(remembered, second))), false,
            {}, {}, { _, _ -> }, {}, {}) } }
        compose.onNodeWithContentDescription("Terminal 1: Codex").performTouchInput { longClick() }
        compose.onNodeWithText("Current program: zsh").assertIsDisplayed()
        compose.onNodeWithText("Last observed program: codex").assertIsDisplayed()
        compose.onNodeWithText("Last program folder: /work/project/src").assertIsDisplayed()
        compose.onNodeWithText("Program names are recovery hints", substring = true).assertExists()
    }

    @Test fun hiddenSingleTerminalStillOffersProgramInformationViaMenuRequest() {
        var requested by mutableStateOf(false)
        val only = first.copy(currentProgram = "vim", directory = "/work")
        compose.setContent { TerminalHubTheme { TmuxWindowBar(state().copy(snapshot = TmuxWindowsSnapshot("\$1", listOf(only))), false,
            {}, {}, { _, _ -> }, {}, {}, infoRequested = requested, onInfoRequestHandled = { requested = false }) } }
        compose.onNodeWithTag("project-terminal-bar").assertDoesNotExist()
        compose.runOnIdle { requested = true }
        compose.onNodeWithText("Current program: vim").assertIsDisplayed()
        compose.onNodeWithText("DONE").performClick()
        compose.onNodeWithText("Current program: vim").assertDoesNotExist()
    }

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

    @Test fun singleTerminalHidesRowButMenuRequestCanCreateAnotherAndInputStillBlocksSwitching() {
        var busy by mutableStateOf(false)
        var current by mutableStateOf(state().copy(snapshot = TmuxWindowsSnapshot("\$1", listOf(first))))
        var requested by mutableStateOf(false)
        compose.setContent { TerminalHubTheme { TmuxWindowBar(current, busy, { current = state() }, { fail("Must not switch while sending input") }, { _, _ -> }, {}, {},
            createRequested = requested, onCreateRequestHandled = { requested = false }) } }
        compose.onNodeWithTag("project-terminal-bar").assertDoesNotExist()
        compose.runOnIdle { requested = true }
        compose.onNodeWithText("Name (optional)").assertIsDisplayed()
        compose.onNodeWithText("SAVE").performClick()
        compose.onNodeWithContentDescription("Terminal 2: Tests").assertIsDisplayed()
        compose.runOnIdle { busy = true }
        compose.onNodeWithContentDescription("Terminal 2: Tests").assertIsNotEnabled()
        compose.onNodeWithContentDescription("New terminal in project").assertIsNotEnabled()
        compose.onNodeWithText("Sending input — terminal switching is paused").assertIsDisplayed()
    }

    @Test fun terminalTabDimensionsChangeImmediatelyAndLargeTextHasRoom() {
        var height by mutableIntStateOf(28)
        var width by mutableIntStateOf(100)
        var text by mutableIntStateOf(12)
        compose.setContent { TerminalHubTheme { TmuxWindowBar(state(), false, {}, {}, { _, _ -> }, {}, {},
            tabWidth = width, tabHeight = height, tabTextSize = text) } }
        compose.onNodeWithContentDescription("Terminal 1: Codex").assertWidthIsEqualTo(100.dp).assertHeightIsEqualTo(28.dp)
        compose.onNodeWithTag("project-terminal-bar").assertHeightIsEqualTo(28.dp)
        compose.runOnIdle { width = 150; height = 48; text = 20 }
        compose.onNodeWithContentDescription("Terminal 1: Codex").assertWidthIsEqualTo(150.dp).assertHeightIsEqualTo(48.dp)
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
