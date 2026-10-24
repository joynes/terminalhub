package se.joynes.terminalhub

import androidx.compose.runtime.*
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.joynes.terminalhub.data.db.entity.PinnedActionEntity
import se.joynes.terminalhub.data.model.PinnedMacroCodec
import se.joynes.terminalhub.domain.*
import se.joynes.terminalhub.ui.screen.sessions.*
import se.joynes.terminalhub.ui.theme.TerminalHubTheme

@RunWith(AndroidJUnit4::class)
class PinnedMacroUiTest {
    @get:Rule val compose = createComposeRule()

    private fun scrollTo(text: String) {
        compose.onNodeWithTag("pinned-action-editor").performScrollToNode(hasText(text))
    }

    @Test fun templateCreatesCtrlCTextEnterWithDefaultGlobalScope() {
        var saved: PinnedActionEntity? = null
        compose.setContent { TerminalHubTheme {
            PinnedActionEditor(PinnedActionEntity(name = "", text = "git status", scope = "GLOBAL"), 7, { saved = it }, {})
        } }
        compose.onNodeWithText("Keyboard macro").performClick()
        scrollTo("Template: Ctrl+C → text → Enter")
        compose.onNodeWithText("Template: Ctrl+C → text → Enter").performClick()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "macro-editor.png").outputStream().use {
            compose.onNodeWithTag("pinned-action-editor-dialog").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle {
            assertNotNull(saved)
            assertEquals(TerminalMacro.interruptAndSend("git status"), PinnedMacroCodec.decode(saved!!.macroJson))
            assertFalse(saved!!.sendEnter)
            assertEquals("GLOBAL", saved!!.scope)
            assertNull(saved!!.projectId)
            assertTrue(saved!!.name.isNotBlank())
        }
    }

    @Test fun stepsCanBeReorderedDeletedDuplicatedAndPersistedInChosenOrder() {
        var saved: PinnedActionEntity? = null
        val original = listOf(MacroStep.Key("TAB"), MacroStep.Text("hello"), MacroStep.Key("ENTER"))
        compose.setContent { TerminalHubTheme {
            PinnedActionEditor(PinnedActionEntity(name = "Macro", text = "summary", macroJson = PinnedMacroCodec.encode(original), projectId = 7), 7, { saved = it }, {})
        } }
        scrollTo("Text for step 2")
        compose.onNodeWithContentDescription("Move step 2 up").performClick()
        scrollTo("Text for step 1")
        compose.onNodeWithTag("pinned-action-editor").performScrollToNode(hasText("Duplicate") and hasAnyAncestor(hasTestTag("macro-step-1")))
        compose.onNode(hasText("Duplicate") and hasAnyAncestor(hasTestTag("macro-step-1"))).performClick()
        scrollTo("Text for step 2")
        compose.onNodeWithContentDescription("Delete step 2").performClick()
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle {
            assertEquals(listOf(MacroStep.Text("hello"), MacroStep.Key("TAB"), MacroStep.Key("ENTER")), PinnedMacroCodec.decode(saved!!.macroJson))
            assertEquals(7L, saved!!.projectId)
        }
    }

    @Test fun keyModifiersAndRepeatAreEditableAndInvalidPauseDisablesSave() {
        var saved: PinnedActionEntity? = null
        val initial = PinnedActionEntity(name = "Keys", text = "summary", macroJson = PinnedMacroCodec.encode(listOf(MacroStep.Key("C"), MacroStep.Pause(300))))
        compose.setContent { TerminalHubTheme { PinnedActionEditor(initial, 1, { saved = it }, {}) } }
        scrollTo("Ctrl")
        compose.onNodeWithText("Ctrl", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Alt", useUnmergedTree = true).performClick()
        scrollTo("Repeat step 1")
        compose.onNodeWithText("Repeat step 1").performTextReplacement("2")
        scrollTo("Pause 2 (milliseconds)")
        compose.onNodeWithText("Pause 2 (milliseconds)").performTextReplacement("999999")
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithText("Pause 2 (milliseconds)").performTextReplacement("500")
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle { assertEquals(listOf(MacroStep.Key("C", ctrl = true, alt = true, repeat = 2), MacroStep.Pause(500)), PinnedMacroCodec.decode(saved!!.macroJson)) }
    }

    @Test fun userCanBuildMacroFromBlankAndCancelWithoutSaving() {
        var saved = false
        var closed = false
        compose.setContent { TerminalHubTheme {
            PinnedActionEditor(PinnedActionEntity(name = "", text = "", scope = "GLOBAL"), 1, { saved = true }, { closed = true })
        } }
        compose.onNodeWithText("Keyboard macro").performClick()
        compose.onNodeWithText("Save").assertIsNotEnabled()
        scrollTo("+ Key"); compose.onNodeWithText("+ Key").performClick()
        compose.onNodeWithText("Save").assertIsEnabled()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertFalse(saved); assertTrue(closed) }
    }

    @Test fun macroTapRunsEvenThoughLegacySendEnterIsFalseAndNeverPreparesSummaryAsText() {
        var sent: PinnedActionEntity? = null
        val macro = PinnedActionEntity(id = 1, name = "Stop and send", text = "summary never pasted", sendEnter = false,
            macroJson = PinnedMacroCodec.encode(TerminalMacro.interruptAndSend("hello")))
        compose.setContent { TerminalHubTheme {
            PinnedActionsSheet(1, listOf(macro), emptyList(), onDismiss = {}, onPrepare = { error("Macro must execute its steps") },
                onSend = { sent = it }, onSave = {}, onDeletePin = {}, onDeleteHistory = {})
        } }
        compose.onNodeWithText("Stop and send").performClick()
        compose.runOnIdle { assertEquals(macro, sent) }
        compose.onNodeWithText("Stop and send").performTouchInput { longClick() }
        compose.onNodeWithText("Prepare in Text Input").assertDoesNotExist()
        compose.onNodeWithText("Edit action").assertExists()
    }

    @Test fun runningStatusShowsProgressAndStopThenCompletionCanBeDismissed() {
        var state by mutableStateOf(MacroRunState("Stop and send", "project-one", 2, 4))
        var stopped = false
        var dismissed = false
        compose.setContent { TerminalHubTheme {
            PinnedMacroStatus(state, onStop = { stopped = true; state = state.copy(running = false, message = "Macro stopped") }, onDismiss = { dismissed = true })
        } }
        compose.onNodeWithText("Step 2/4").assertIsDisplayed()
        compose.onNodeWithText("STOP").performClick()
        compose.onNodeWithText("Macro stopped").assertIsDisplayed()
        compose.onNodeWithText("CLOSE").performClick()
        compose.runOnIdle { assertTrue(stopped); assertTrue(dismissed) }
    }
}
