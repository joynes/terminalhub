package se.joynes.terminalhub

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import se.joynes.terminalhub.ui.screen.sessions.ProjectRenameDialog
import se.joynes.terminalhub.ui.navigation.SessionTabBar
import se.joynes.terminalhub.ui.screen.sessions.ProjectTabState
import se.joynes.terminalhub.ui.theme.TerminalHubTheme

class ProjectRenameDialogTest {
    @get:Rule val rule = createComposeRule()

    @Test fun longPressRenamesTheSelectedProjectEvenWhenDisconnected() {
        var title by mutableStateOf("existing-folder")
        var renaming by mutableStateOf(false)
        var saved = 0
        rule.setContent {
            TerminalHubTheme {
                SessionTabBar(tabs = listOf(ProjectTabState(projectId = 7, projectName = title, sessionId = null,
                    isConnected = false, isConnecting = false, colorSeed = 42, usesTmux = true)), activeId = null,
                    onSelect = {}, onClose = { _, _ -> }, onReorder = {}, onAddProject = {},
                    onRenameProject = { assertEquals(7L, it); renaming = true })
                if (renaming) ProjectRenameDialog(title, onDismiss = { renaming = false }, onSave = {
                    title = it; saved++; renaming = false
                })
            }
        }
        rule.onNodeWithTag("project-tab-7").performTouchInput { longClick() }
        rule.onNodeWithText("Rename project").performClick()
        rule.onNodeWithText("Display name").assertExists()
        rule.onNodeWithText("existing-folder", useUnmergedTree = true).assertExists()
        rule.onNode(hasSetTextAction()).performTextReplacement("Mitt projekt åäö")
        rule.onNodeWithText("SAVE").performClick()
        rule.onNodeWithTag("project-tab-7").assertTextContains("MITT PROJEKT ÅÄÖ")
        rule.runOnIdle { assertEquals(1, saved) }
    }

    @Test fun blankNameCannotBeSavedAndCancelDoesNotRename() {
        var dismissed = false
        rule.setContent { TerminalHubTheme {
            ProjectRenameDialog("demo", onDismiss = { dismissed = true }, onSave = { error("Must not save") })
        } }
        rule.onNode(hasSetTextAction()).performTextReplacement(" ")
        rule.onNodeWithText("SAVE").assertIsNotEnabled()
        rule.onNodeWithText("CANCEL").performClick()
        rule.runOnIdle { assertTrue(dismissed) }
    }
}
