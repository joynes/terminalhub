package se.joynes.terminalhub

import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.key.Key
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
@OptIn(ExperimentalTestApi::class)
class TmuxWindowBarTest {
    @Test fun projectRequestEditsOnlyWindowDirectly() {
        var requested by mutableStateOf(true)
        var saved: Pair<TmuxWindow, String>? = null
        val single = first.copy(key = "default-window")
        compose.setContent { TerminalHubTheme { TmuxWindowBar(
            state().copy(snapshot = TmuxWindowsSnapshot("\$1", listOf(single))), false, {}, {}, { _, _ -> }, {}, {},
            editStartCommandRequested = requested, onEditStartCommandRequestHandled = { requested = false },
            onSaveStartCommand = { window, command -> saved = window to command }) } }
        compose.onNodeWithText("Command").performTextReplacement("./backup.sh")
        compose.onNodeWithText("SAVE").performClick()
        compose.runOnIdle { assertEquals(single to "./backup.sh", saved); assertFalse(requested) }
    }
    @Test fun startCommandCanBeSavedPreparedAndRemovedForHiddenSingleWindow() {
        var window by mutableStateOf(first.copy(key = "stable-key"))
        var info by mutableStateOf(true)
        var prepared: String? = null
        compose.setContent { TerminalHubTheme { TmuxWindowBar(
            state().copy(snapshot = TmuxWindowsSnapshot("\$1", listOf(window))), false, {}, {}, { _, _ -> }, {}, {},
            infoRequested = info, onInfoRequestHandled = { info = false },
            onSaveStartCommand = { _, command -> window = window.copy(startCommand = command) },
            onPrepareStartCommand = { prepared = it.startCommand }) } }
        compose.onNodeWithText("Edit start command").performScrollTo().performClick()
        compose.onNodeWithText("Command").performTextReplacement("python report.py --year 2026")
        compose.onNodeWithText("SAVE").performClick()
        compose.runOnIdle { assertNull(prepared); info = true }
        compose.onNodeWithText("Prepare start command").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("python report.py --year 2026", prepared); info = true }
        compose.onNodeWithText("Edit start command").performScrollTo().performClick()
        compose.onNodeWithText("Command").assertTextContains("python report.py --year 2026")
        compose.onNodeWithText("REMOVE").performClick()
        compose.runOnIdle { info = true }
        compose.onNodeWithText("Prepare start command").assertDoesNotExist()
    }

    @Test fun startCommandCannotBePreparedForInactiveTerminal() {
        val other = second.copy(key = "other-key", startCommand = "tail -f app.log")
        compose.setContent { TerminalHubTheme { TmuxWindowBar(
            state().copy(snapshot = TmuxWindowsSnapshot("\$1", listOf(first, other))), false, {}, {}, { _, _ -> }, {}, {},
            onPrepareStartCommand = { fail("Must select the terminal first") }) } }
        compose.onNodeWithContentDescription("Terminal 2: Tests").performTouchInput { longClick() }
        compose.onNodeWithText("Prepare start command").performScrollTo().assertIsNotEnabled()
    }
    @Test fun renameShortcutWorksForHiddenSingleWindowAndEnterSavesOnce() {
        var requested by mutableStateOf(false)
        var renamed: Pair<TmuxWindow, String>? = null
        var calls = 0
        val single = state().copy(snapshot = TmuxWindowsSnapshot("\$1", listOf(first)))
        compose.setContent { TerminalHubTheme { TmuxWindowBar(single, false, {}, {},
            { window, name -> renamed = window to name; calls++ }, {}, {},
            renameRequested = requested, onRenameRequestHandled = { requested = false }) } }
        compose.onNodeWithTag("project-terminal-bar").assertDoesNotExist()
        compose.runOnIdle { requested = true }
        compose.onNodeWithText("Terminal name").assertIsFocused().performTextReplacement("Build logs")
        compose.onNodeWithText("Terminal name").performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle { assertEquals(first to "Build logs", renamed); assertEquals(1, calls); assertFalse(requested) }
        compose.onNodeWithText("Rename terminal").assertDoesNotExist()
    }

    @Test fun renameShortcutCancelDoesNotChangeWindowAndBusyInputBlocksRequest() {
        var requested by mutableStateOf(false)
        var busy by mutableStateOf(false)
        compose.setContent { TerminalHubTheme { TmuxWindowBar(state(), busy, {}, {},
            { _, _ -> fail("Must not rename") }, {}, {}, renameRequested = requested, onRenameRequestHandled = { requested = false }) } }
        compose.runOnIdle { requested = true }
        compose.onNodeWithText("Terminal name").performTextReplacement("Other")
        compose.onNodeWithText("CANCEL").performClick()
        compose.runOnIdle { busy = true; requested = true }
        compose.onNodeWithText("Rename terminal").assertDoesNotExist()
    }
    @Test fun singleWindowCanBePinnedFromInformationAndUnpinnedFromVisibleTab() {
        val window = first.copy(key = "stable-window")
        val single = state().copy(snapshot = TmuxWindowsSnapshot("\$1", listOf(window)))
        var pins by mutableStateOf(emptySet<TerminalWorkPin>())
        var info by mutableStateOf(true)
        compose.setContent { TerminalHubTheme { TmuxWindowBar(single, false, {}, {}, { _, _ -> }, {}, {},
            infoRequested = info, onInfoRequestHandled = { info = false }, workPins = pins,
            onToggleWorkPin = { pins = if (pins.isEmpty()) setOf(TerminalWorkPin(1, it.key, it.name)) else emptySet() }) } }
        compose.onNodeWithText("Mark as active work").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Terminal 1: Codex, active work").assertIsSelected()
            .performTouchInput { longClick() }
        compose.onNodeWithText("Unmark active work").performScrollTo().performClick()
        compose.onNodeWithTag("project-terminal-bar").assertDoesNotExist()
    }

    @Test fun workPinsSurviveStoreRecreationAndExternalClear() {
        val base = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val file = "work-pin-test-${System.nanoTime()}"
        val context = object : android.content.ContextWrapper(base) {
            override fun getApplicationContext(): android.content.Context = this
            override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences = base.getSharedPreferences(file, mode)
        }
        val store = TerminalWorkPins(context)
        val pin = TerminalWorkPin(1, "window-key", "Codex")
        var reopened: TerminalWorkPins? = null
        try {
            store.toggle(pin)
            reopened = TerminalWorkPins(context)
            assertEquals(setOf(pin), reopened.pins.value)
            reopened.toggle(pin.copy(name = "Renamed"))
            assertTrue(reopened.pins.value.isEmpty())
            store.toggle(pin)
            compose.runOnIdle { base.getSharedPreferences(file, 0).edit().clear().commit() }
            compose.waitUntil { store.pins.value.isEmpty() }
        } finally {
            store.close(); reopened?.close()
            base.getSharedPreferences(file, 0).edit().clear().commit()
        }
    }

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
