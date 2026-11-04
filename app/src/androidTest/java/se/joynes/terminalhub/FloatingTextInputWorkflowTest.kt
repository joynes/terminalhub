package se.joynes.terminalhub

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import se.joynes.terminalhub.ui.screen.sessions.FloatingTextInputDialog
import se.joynes.terminalhub.ui.theme.TerminalHubTheme

class FloatingTextInputWorkflowTest {
    @get:Rule val compose = createComposeRule()
    @Test fun historySelectionKeepsOriginalMultilineTextAndImeSendRecordsBeforeDismiss() {
        var text by mutableStateOf(TextFieldValue(""))
        val events = mutableListOf<String>()
        compose.setContent { TerminalHubTheme { FloatingTextInputDialog(text, { text = it },
            { events += "send:$it" }, { events += "dismiss" }, history = listOf("git status", "first\nsecond"),
            onSaveHistory = { events += "history:$it" }) } }
        compose.onNodeWithContentDescription("Terminal text input").performImeAction()
        compose.runOnIdle { assertTrue(events.isEmpty()) }
        compose.onNodeWithText("▾ HISTORY").performClick()
        compose.onNodeWithText("first second").performClick()
        compose.runOnIdle { assertEquals("first\nsecond", text.text); assertEquals(text.text.length, text.selection.start) }
        compose.onNodeWithContentDescription("Terminal text input").performImeAction()
        compose.runOnIdle { assertEquals(listOf("history:first\nsecond", "send:first\nsecond", "dismiss"), events) }
    }
    @Test fun compatibilityEditorChangesTextAndLocalOpacityAndDraggingStayInsideContainer() {
        var text by mutableStateOf("original")
        var sent = ""
        compose.setContent { TerminalHubTheme { FloatingTextInputDialog(text, { text = it }, { sent = it }, {}) } }
        compose.onNodeWithContentDescription("Terminal text input").performTextReplacement("edited")
        compose.onNodeWithContentDescription("Text input opacity").performSemanticsAction(SemanticsActions.SetProgress) { it(.4f) }
        compose.onNodeWithText("40%").assertExists()
        compose.onNodeWithText("TEXT INPUT").performTouchInput { swipe(center, center + Offset(-500f, -400f), 500) }
        val bounds = compose.onNodeWithText("TEXT INPUT").fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.left >= 0); assertTrue(bounds.top >= 0)
        compose.onNodeWithContentDescription("Terminal text input").performImeAction()
        compose.runOnIdle { assertEquals("edited", sent) }
    }
    @Test fun externalHistoryAndOpacityCallbacksRemainOwnedByTheProject() {
        var opens = 0; var opacity = 0f; var dismissals = 0
        compose.setContent { TerminalHubTheme { FloatingTextInputDialog(TextFieldValue("draft"), {}, {}, { dismissals++ },
            history = listOf("history marker"), onOpenHistory = { opens++ }, panelOpacity = .8f,
            onPanelOpacityChange = { opacity = it }) } }
        compose.onNodeWithText("▾ HISTORY").performClick()
        compose.onNodeWithText("history marker").assertDoesNotExist()
        compose.onNodeWithContentDescription("Text input opacity").performSemanticsAction(SemanticsActions.SetProgress) { it(.6f) }
        compose.onNodeWithText("✕").performClick()
        compose.runOnIdle { assertEquals(1, opens); assertEquals(.6f, opacity); assertEquals(1, dismissals) }
    }
}
