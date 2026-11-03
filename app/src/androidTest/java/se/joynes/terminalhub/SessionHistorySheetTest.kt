package se.joynes.terminalhub

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import se.joynes.terminalhub.domain.TerminalSessionId
import se.joynes.terminalhub.domain.TerminalSessionMeta
import se.joynes.terminalhub.ui.screen.sessions.SessionHistorySheet
import se.joynes.terminalhub.ui.theme.TerminalHubTheme

class SessionHistorySheetTest {
    @get:Rule val compose = createComposeRule()
    private fun meta(id: Long, minutes: Long, connected: Boolean = true) = TerminalSessionMeta(
        TerminalSessionId("session-$id"), "Project $id", id, isConnected = connected,
        hasUnreadOutput = false, previewLines = emptyList(), lastOpenedAt = System.currentTimeMillis() - minutes * 60_000)
    @Test fun emptyHistoryExplainsThatNoSessionsExist() {
        compose.setContent { TerminalHubTheme { SessionHistorySheet(emptyList(), emptyList(), null, {}, {}, {}) } }
        compose.onNodeWithText("NO SESSIONS").assertIsDisplayed()
        compose.onNodeWithText("CLOSED").assertDoesNotExist()
    }
    @Test fun openProjectsAreSortedByRecentUseAndSelectingOneDismissesTheSheet() {
        val older = meta(1, 5, false); val recent = meta(2, 0)
        var selected: TerminalSessionId? = null; var dismissals = 0
        compose.setContent { TerminalHubTheme {
            SessionHistorySheet(listOf(older, recent), emptyList(), recent.id, { selected = it }, {}, { dismissals++ })
        } }
        compose.onNodeWithText("last opened just now").assertExists()
        compose.onNodeWithText("last opened 5m ago").assertExists()
        val recentBounds = compose.onNodeWithText("PROJECT 2").fetchSemanticsNode().boundsInRoot
        val olderBounds = compose.onNodeWithText("PROJECT 1").fetchSemanticsNode().boundsInRoot
        assertTrue(recentBounds.top < olderBounds.top)
        compose.onNodeWithText("PROJECT 1").performClick()
        compose.runOnIdle { assertEquals(older.id, selected); assertEquals(1, dismissals) }
    }
    @Test fun closedProjectsCanBeSelectedDeselectedAndReopenedAsABatch() {
        val reopened = mutableListOf<Long>(); var dismissals = 0
        compose.setContent { TerminalHubTheme {
            SessionHistorySheet(emptyList(), listOf(meta(3, 90), meta(4, 3000)), null, {}, { reopened += it }, { dismissals++ })
        } }
        compose.onNodeWithText("CLOSED").assertIsDisplayed()
        compose.onNodeWithText("PROJECT 3").performClick()
        compose.onNodeWithText("↩ REOPEN SELECTED (1)").assertIsDisplayed()
        compose.onNodeWithText("PROJECT 3").performClick()
        compose.onNodeWithText("↩ REOPEN SELECTED (1)").assertDoesNotExist()
        compose.onNodeWithText("PROJECT 3").performClick()
        compose.onNodeWithText("PROJECT 4").performClick()
        compose.onNodeWithText("↩ REOPEN SELECTED (2)").performClick()
        compose.runOnIdle { assertEquals(setOf(3L, 4L), reopened.toSet()); assertEquals(1, dismissals) }
    }
    @Test fun individualOpenButtonReopensWithoutChangingCheckboxSelection() {
        var reopened: Long? = null; var dismissed = false
        compose.setContent { TerminalHubTheme {
            SessionHistorySheet(emptyList(), listOf(meta(8, 0)), null, {}, { reopened = it }, { dismissed = true })
        } }
        compose.onNodeWithText("↩ OPEN").performClick()
        compose.runOnIdle { assertEquals(8L, reopened); assertTrue(dismissed) }
    }
}
