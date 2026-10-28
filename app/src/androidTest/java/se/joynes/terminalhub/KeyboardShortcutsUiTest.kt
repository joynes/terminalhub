package se.joynes.terminalhub

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.compose.material3.Surface
import se.joynes.terminalhub.ui.theme.TerminalHubTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import se.joynes.terminalhub.data.settings.*
import se.joynes.terminalhub.ui.components.*
import se.joynes.terminalhub.ui.screen.settings.KeyboardShortcutsEditor

class KeyboardShortcutsUiTest {
    @Test fun closedProjectToggleStartsOffAndCanBeEnabled() {
        var includeClosed by mutableStateOf(false)
        content { QuickSwitchDialog("Find", emptyList(), {}, {}, includeClosed = includeClosed,
            onIncludeClosedChange = { includeClosed = it }) }
        compose.onNode(isToggleable()).assertIsOff()
        compose.onNodeWithText("Include closed projects").performClick()
        compose.onNode(isToggleable()).assertIsOn()
    }
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun content(body: @Composable () -> Unit) {
        compose.setContent { TerminalHubTheme { Surface { body() } } }
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        assertTrue(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            .takeScreenshot(File(compose.activity.cacheDir, name)))
    }
    private fun event(code: Int, action: Int = KeyEvent.ACTION_DOWN, meta: Int = 0, repeat: Int = 0, flags: Int = 0): KeyEvent {
        val now = SystemClock.uptimeMillis()
        return KeyEvent(now, now, action, code, repeat, meta, -1, 0, flags, InputDevice.SOURCE_KEYBOARD)
    }
    private fun press(code: Int, meta: Int = 0) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.sendKeySync(event(code, meta = meta))
        instrumentation.sendKeySync(event(code, KeyEvent.ACTION_UP, meta))
        compose.waitForIdle()
    }
    @Test fun dispatcherConsumesRepeatsAndReleaseWithoutModifiersButLeavesTypingAlone() {
        val dispatcher = ShortcutKeyDispatcher()
        val bindings = KeyboardShortcuts.parse(listOf("FIND_PROJECT=CTRL+P"))
        val actions = mutableListOf<ShortcutAction>()
        assertTrue(dispatcher.dispatch(event(KeyEvent.KEYCODE_P, meta = KeyEvent.META_CTRL_ON), bindings, actions::add))
        assertTrue(dispatcher.dispatch(event(KeyEvent.KEYCODE_P, repeat = 1), bindings, actions::add))
        assertTrue(dispatcher.dispatch(event(KeyEvent.KEYCODE_P, KeyEvent.ACTION_UP), bindings, actions::add))
        assertEquals(listOf(ShortcutAction.FIND_PROJECT), actions)
        assertFalse(dispatcher.dispatch(event(KeyEvent.KEYCODE_P), bindings, actions::add))
        assertFalse(dispatcher.dispatch(event(KeyEvent.KEYCODE_DEL), bindings, actions::add))
        assertFalse(dispatcher.dispatch(event(KeyEvent.KEYCODE_P, meta = KeyEvent.META_CTRL_ON, flags = KeyEvent.FLAG_SOFT_KEYBOARD), bindings, actions::add))
    }
    @Test fun recordingCapturesActualWindowKeysSavesAndReportsConflicts() {
        var rows by mutableStateOf(emptyList<String>())
        content { Column(Modifier.verticalScroll(rememberScrollState())) { KeyboardShortcutsEditor(rows) { rows = it } } }
        compose.onNodeWithText("Next project").performClick()
        press(KeyEvent.KEYCODE_P, KeyEvent.META_CTRL_ON)
        compose.onNodeWithText("CTRL + P").assertIsDisplayed()
        screenshot("shortcut-capture.png")
        compose.onNodeWithText("SAVE").performClick()
        compose.runOnIdle { assertEquals(listOf("NEXT_PROJECT=CTRL+P"), rows) }
        compose.onNodeWithText("Previous project").performClick()
        press(KeyEvent.KEYCODE_P, KeyEvent.META_CTRL_ON)
        compose.onNodeWithText("Already assigned to Next project. Clear that action first or choose another combination.").assertIsDisplayed()
        compose.onNodeWithText("SAVE").assertIsNotEnabled()
        press(KeyEvent.KEYCODE_ESCAPE)
        compose.onNodeWithText("SAVE").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, rows.size) }
    }
    @Test fun finderFiltersFuzzilyAndEnterSelectsStableId() {
        var chosen: String? = null
        content { QuickSwitchDialog("Find project", listOf(
            QuickSwitchTarget("1", "TerminalHub", "Production · Closed"),
            QuickSwitchTarget("2", "Music", "Open")), { chosen = it }, {}) }
        compose.onNodeWithTag("quick-switch-query").performTextInput("trm")
        compose.onNodeWithText("Music").assertDoesNotExist()
        screenshot("shortcut-finder.png")
        press(KeyEvent.KEYCODE_ENTER)
        compose.runOnIdle { assertEquals("1", chosen) }
    }
    @Test fun finderArrowsChooseDuplicateNamesByIdAndEscapeCancels() {
        var chosen: String? = null
        var dismissed = false
        content { QuickSwitchDialog("Find window", listOf(
            QuickSwitchTarget("@1", "shell", "1 · current"),
            QuickSwitchTarget("@2", "shell", "2")), { chosen = it }, { dismissed = true }) }
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        press(KeyEvent.KEYCODE_ENTER)
        compose.runOnIdle { assertEquals("@2", chosen) }
        press(KeyEvent.KEYCODE_ESCAPE)
        compose.runOnIdle { assertTrue(dismissed) }
    }
    @Test fun finderReadlineNavigationMovesSelectionWithoutChangingQuery() {
        var chosen: String? = null
        content { QuickSwitchDialog("Find", listOf(
            QuickSwitchTarget("project:1", "test", "Project · Open"),
            QuickSwitchTarget("project:2", "test", "Project · Open"),
            QuickSwitchTarget("window:@1", "test", "Window · Current")), { chosen = it }, {}) }
        compose.onNodeWithTag("quick-switch-query").performTextInput("test")
        press(KeyEvent.KEYCODE_P, KeyEvent.META_CTRL_ON) // First result stays selected.
        press(KeyEvent.KEYCODE_ENTER)
        compose.runOnIdle { assertEquals("project:1", chosen) }
        repeat(3) { press(KeyEvent.KEYCODE_N, KeyEvent.META_CTRL_ON) }
        press(KeyEvent.KEYCODE_ENTER)
        compose.runOnIdle { assertEquals("window:@1", chosen) }
        press(KeyEvent.KEYCODE_P, KeyEvent.META_CTRL_ON)
        press(KeyEvent.KEYCODE_ENTER)
        compose.runOnIdle { assertEquals("project:2", chosen) }
        compose.onNodeWithTag("quick-switch-query").assertTextContains("test", substring = false)
        // AltGr-like chords must not become finder navigation.
        press(KeyEvent.KEYCODE_N, KeyEvent.META_CTRL_ON or KeyEvent.META_ALT_ON)
        press(KeyEvent.KEYCODE_ENTER)
        compose.runOnIdle { assertEquals("project:2", chosen) }
    }
    @Test fun finderReadlineNavigationSafelyHandlesNoMatches() {
        var chosen: String? = null
        content { QuickSwitchDialog("Find", emptyList(), { chosen = it }, {}) }
        press(KeyEvent.KEYCODE_N, KeyEvent.META_CTRL_ON)
        press(KeyEvent.KEYCODE_P, KeyEvent.META_CTRL_ON)
        press(KeyEvent.KEYCODE_ENTER)
        compose.onNodeWithText("OPEN").assertIsNotEnabled()
        compose.runOnIdle { assertNull(chosen) }
    }
    @Test fun noMatchCannotOpenAnything() {
        var chosen: String? = null
        content { QuickSwitchDialog("Find project", listOf(QuickSwitchTarget("1", "Music", "Open")), { chosen = it }, {}) }
        compose.onNodeWithTag("quick-switch-query").performTextInput("zzz")
        compose.onNodeWithText("No matches").assertIsDisplayed()
        compose.onNodeWithText("OPEN").assertIsNotEnabled()
        press(KeyEvent.KEYCODE_ENTER)
        compose.runOnIdle { assertNull(chosen) }
    }
    @Test fun unifiedFindShowsProjectsAndWindowsTogetherAndSelectsDistinctTargets() {
        var chosen: String? = null
        content { QuickSwitchDialog("Find", listOf(
            QuickSwitchTarget("project:1", "Build", "Project · Production"),
            QuickSwitchTarget("window:@1", "Build", "Window · TerminalHub")), { chosen = it }, {}) }
        compose.onNodeWithTag("quick-switch-query").performTextInput("bld")
        compose.onNodeWithText("Project · Production").assertIsDisplayed()
        compose.onNodeWithText("Window · TerminalHub").assertIsDisplayed()
        press(KeyEvent.KEYCODE_ENTER)
        compose.runOnIdle { assertEquals("project:1", chosen) }
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        press(KeyEvent.KEYCODE_ENTER)
        compose.runOnIdle { assertEquals("window:@1", chosen) }
    }
    @Test fun shortcutBindingsUpdateAndWindowCallbackRestoresWhenLeavingScreen() {
        var enabled by mutableStateOf(true)
        var rows by mutableStateOf(listOf("FIND_PROJECT=CTRL+P"))
        val calls = mutableListOf<ShortcutAction>()
        val previous = compose.activity.window.callback
        content { if (enabled) HardwareShortcutHandler(rows) { calls += it } }
        press(KeyEvent.KEYCODE_P, KeyEvent.META_CTRL_ON)
        compose.runOnIdle { rows = listOf("NEW_WINDOW=CTRL+P") }
        press(KeyEvent.KEYCODE_P, KeyEvent.META_CTRL_ON)
        compose.runOnIdle { assertEquals(listOf(ShortcutAction.FIND_PROJECT, ShortcutAction.NEW_WINDOW), calls); enabled = false }
        compose.runOnIdle { assertSame(previous, compose.activity.window.callback) }
    }
}
