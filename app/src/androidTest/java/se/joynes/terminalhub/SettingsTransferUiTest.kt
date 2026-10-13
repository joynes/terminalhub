package se.joynes.terminalhub

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import se.joynes.terminalhub.data.settings.*
import se.joynes.terminalhub.ui.screen.settings.*
import se.joynes.terminalhub.ui.theme.TerminalHubTheme
import androidx.compose.material3.Text

class SettingsTransferUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun choicesClearlySeparateAiSettingsFromFullBackupAndBusyBlocksDoubleClicks() {
        var selected = ""
        var busy by mutableStateOf(false)
        rule.setContent { TerminalHubTheme {
            SettingsTransferChoices(busy, { selected = "settings-export" }, { selected = "settings-import" },
                { selected = "backup-export" }, { selected = "backup-import" })
        } }
        rule.onNodeWithText("1 · AI SETTINGS — PREFERENCES ONLY").assertExists()
        rule.onNodeWithText("2 · FULL APP CONFIGURATION — NEW PHONE / BACKUP").assertExists()
        rule.onNodeWithText("EXPORT SETTINGS").performClick()
        rule.runOnIdle { assertEquals("settings-export", selected) }
        rule.onNodeWithText("RESTORE BACKUP").performClick()
        rule.runOnIdle { assertEquals("backup-import", selected); busy = true }
        rule.onNodeWithText("EXPORT SETTINGS").assertIsNotEnabled()
        rule.onNodeWithText("RESTORE BACKUP").assertIsNotEnabled()
    }

    @Test fun importPreviewShowsDiffAndSensitiveValuesRequireAnUncheckedApprovalBox() {
        val preview = SettingsYaml.preview("format: terminalhub-settings\nversion: 1\nsettings:\n  inputHistoryEnabled: true", AppSettings())
        var applied: Boolean? = null
        rule.setContent { TerminalHubTheme { SettingsImportPreviewDialog(preview, {}, { applied = it }) } }
        rule.onNodeWithText("inputHistoryEnabled\nfalse → true").assertExists()
        rule.onNodeWithText("APPLY SETTINGS").assertIsNotEnabled()
        rule.onNode(isToggleable()).performScrollTo().assertIsOff().performClick()
        rule.onNodeWithText("APPLY SETTINGS").assertIsEnabled().performClick()
        rule.runOnIdle { assertEquals(true, applied) }
    }

    @Test fun subgroupsStayCollapsedUntilOpenedAndCollapseAgain() {
        rule.setContent { TerminalHubTheme { SettingsSubgroup("Touch scrolling", "Speed and inertia") { Text("Advanced scroll controls") } } }
        rule.onNodeWithText("Advanced scroll controls").assertDoesNotExist()
        rule.onNodeWithText("Touch scrolling").performClick()
        rule.onNodeWithText("Advanced scroll controls").assertExists()
        rule.onNodeWithText("Touch scrolling").performClick()
        rule.onNodeWithText("Advanced scroll controls").assertDoesNotExist()
    }

    @Test fun cancellingPreviewDoesNotApplyAnySetting() {
        val preview = SettingsYaml.preview("format: terminalhub-settings\nversion: 1\nsettings:\n  preferFastResume: false", AppSettings())
        var cancelled = false
        rule.setContent { TerminalHubTheme { SettingsImportPreviewDialog(preview, { cancelled = true }, { error("Cancel must not apply") }) } }
        rule.onNodeWithText("CANCEL").performClick()
        rule.runOnIdle { assertTrue(cancelled) }
    }
}
