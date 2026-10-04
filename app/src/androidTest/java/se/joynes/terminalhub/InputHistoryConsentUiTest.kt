package se.joynes.terminalhub

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.joynes.terminalhub.data.db.entity.*
import se.joynes.terminalhub.ui.components.InputHistorySettings
import se.joynes.terminalhub.ui.screen.sessions.PinnedActionsSheet
import se.joynes.terminalhub.ui.theme.TerminalHubTheme

@RunWith(AndroidJUnit4::class)
class InputHistoryConsentUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun settingsRequiresConsentEveryEnableAndConfirmationBeforeDelete() {
        var enabled by mutableStateOf(false)
        var deleted = 0
        rule.setContent { TerminalHubTheme { InputHistorySettings(enabled, { enabled = it }, { deleted++ }) } }
        repeat(2) {
            rule.onNodeWithContentDescription("Save terminal input history").performClick()
            rule.runOnIdle { assertFalse(enabled) }
            rule.onNodeWithText("CANCEL").performClick()
            rule.runOnIdle { assertFalse(enabled) }
            rule.onNodeWithContentDescription("Save terminal input history").performClick()
            rule.onNodeWithText("ENABLE HISTORY").performClick()
            rule.runOnIdle { assertTrue(enabled) }
            rule.onNodeWithContentDescription("Save terminal input history").performClick()
            rule.runOnIdle { assertFalse(enabled) }
        }
        rule.onNodeWithText("Delete saved input history").performClick()
        rule.runOnIdle { assertEquals(0, deleted) }
        rule.onNodeWithText("CANCEL").performClick()
        rule.onNodeWithText("Delete saved input history").performClick()
        rule.onNodeWithText("DELETE HISTORY").performClick()
        rule.runOnIdle { assertEquals(1, deleted) }
    }

    @Test fun pinsExecuteWhileOffAndRecentEnablesOnlyAfterConfirmation() {
        var enabled by mutableStateOf(false)
        var sent = 0
        rule.setContent {
            TerminalHubTheme {
                PinnedActionsSheet(1, listOf(PinnedActionEntity(id = 1, name = "Status", text = "git status", scope = "GLOBAL")),
                    listOf(TextInputHistoryEntity(id = 1, projectId = 1, text = "old saved input")),
                    historyEnabled = enabled, onEnableHistory = { enabled = true },
                    onDismiss = {}, onPrepare = {}, onSend = { sent++ }, onSave = {}, onDeletePin = {}, onDeleteHistory = {})
            }
        }
        rule.onNodeWithText("Status").performClick()
        rule.runOnIdle { assertEquals(1, sent); assertFalse(enabled) }
        rule.onNodeWithText("Recent").performClick()
        rule.onNodeWithText("Recent input history is off.").assertExists()
        rule.onNodeWithText("old saved input").assertDoesNotExist()
        rule.onNodeWithText("ENABLE HISTORY").performClick()
        rule.runOnIdle { assertFalse(enabled) }
        rule.onNodeWithText("CANCEL").performClick()
        rule.onNodeWithText("ENABLE HISTORY").performClick()
        rule.onAllNodesWithText("ENABLE HISTORY", useUnmergedTree = true).onLast().performClick()
        rule.runOnIdle { assertTrue(enabled) }
        rule.onNodeWithText("old saved input").assertExists()
    }
}
