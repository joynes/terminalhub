package se.joynes.terminalhub

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import se.joynes.terminalhub.ui.screen.sessions.*
import se.joynes.terminalhub.ui.screen.download.DownloadState
import se.joynes.terminalhub.ui.theme.TerminalHubTheme

class WorkspaceToolsUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun paletteFiltersAndRunsExactlyOneExplicitAction() {
        var calls = 0
        var dismissed = 0
        rule.setContent { TerminalHubTheme { CommandPalette(listOf(
            WorkspaceCommand("reconnect", "Reconnect all", "SSH anslut") { calls++ },
            WorkspaceCommand("settings", "Settings", "inställningar") { fail("Wrong action") }
        ), { dismissed++ }) } }
        rule.onNodeWithTag("command-query").performTextInput("SSH")
        rule.onNodeWithText("Settings").assertDoesNotExist()
        rule.onNodeWithText("Reconnect all").assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(1, calls); assertEquals(1, dismissed) }
    }

    @Test fun unavailableCommandsCannotRun() {
        rule.setContent { TerminalHubTheme { CommandPalette(listOf(
            WorkspaceCommand("search", "Search terminal", enabled = false) { fail("Disabled action ran") }
        ), {}) } }
        rule.onNodeWithText("Search terminal").assertIsNotEnabled()
    }

    @Test fun activityShowsLiveReconnectFeedbackAndPausedTransferWithoutStackingDialogs() {
        var tab by mutableStateOf(ProjectTabState(1, "Music", null, false, connectionError = "Network unavailable"))
        var reconnects = 0
        var shownTransfer: Long? = null
        rule.setContent { TerminalHubTheme { WorkspaceActivityDialog(listOf(tab),
            mapOf(1L to DownloadState.Paused("song.zip", "Network lost", 0)),
            onSelect = {}, onReconnect = { reconnects++; tab = tab.copy(isConnecting = true) },
            onReconnectAll = {}, onTransfer = { shownTransfer = it }, onDismiss = {}) } }
        rule.onNodeWithText("Network unavailable").assertIsDisplayed()
        rule.onNodeWithText("Reconnect", useUnmergedTree = true).performClick()
        rule.onNodeWithText("Connecting…").assertIsDisplayed()
        rule.onNodeWithText("Network unavailable").assertDoesNotExist()
        rule.runOnIdle { assertEquals(1, reconnects) }
        rule.onNodeWithText("Show transfer").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(1L, shownTransfer) }
    }
}
