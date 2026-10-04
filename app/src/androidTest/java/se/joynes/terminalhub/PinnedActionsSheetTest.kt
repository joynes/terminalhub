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
import se.joynes.terminalhub.ui.screen.sessions.PinnedActionsSheet
import se.joynes.terminalhub.ui.screen.terminal.*
import se.joynes.terminalhub.ui.theme.TerminalHubTheme

@RunWith(AndroidJUnit4::class)
class PinnedActionsSheetTest {
    @get:Rule val rule = createComposeRule()

    @Test fun defaultStarOpensSheetAndRecentCanBePinnedThenSent() {
        var visible by mutableStateOf(false)
        var pins by mutableStateOf(emptyList<PinnedActionEntity>())
        var sent: PinnedActionEntity? = null
        rule.setContent {
            TerminalHubTheme {
                SpecialKeyBar(MutableModifierManager(), onKey = { error("Star must not send terminal bytes") }, onPinnedActions = { visible = true })
                if (visible) PinnedActionsSheet(
                    projectId = 1, pins = pins, historyEnabled = true,
                    history = listOf(TextInputHistoryEntity(id = 1, projectId = 1, text = "git status")),
                    onDismiss = { visible = false }, onPrepare = {}, onSend = { sent = it },
                    onSave = { pins = listOf(it.copy(id = 1)) }, onDeletePin = {}, onDeleteHistory = {}
                )
            }
        }
        rule.onNodeWithText("★").performClick()
        rule.onNodeWithText("Recent").performClick()
        rule.onNodeWithText("git status").performTouchInput { longClick() }
        rule.onNodeWithText("Pin", useUnmergedTree = true).performClick()
        rule.onNodeWithText("Name (optional)").performTextInput("Status")
        rule.onNodeWithText("Save").performClick()
        rule.onNodeWithText("Status").performClick()
        rule.runOnIdle {
            assertEquals("git status", sent?.text)
            assertEquals("GLOBAL", sent?.scope)
            assertNull(sent?.projectId)
            assertEquals(true, sent?.sendEnter)
        }
    }

    @Test fun prepareDoesNotSendAndLongPressSupportsEditingAndDeleting() {
        var pins by mutableStateOf(listOf(PinnedActionEntity(id = 2, name = "Draft", text = "hello", scope = "GLOBAL", sendEnter = false)))
        var prepared = ""
        rule.setContent {
            TerminalHubTheme {
                PinnedActionsSheet(1, pins, emptyList(), onDismiss = {},
                    onPrepare = { prepared = it }, onSend = { error("Prepare must not execute") },
                    onSave = { pins = listOf(it) }, onDeletePin = { pins = emptyList() }, onDeleteHistory = {})
            }
        }
        rule.onNodeWithText("Draft").performClick()
        rule.runOnIdle { assertEquals("hello", prepared) }
        rule.onNodeWithText("Draft").performTouchInput { longClick() }
        rule.onNodeWithText("Edit action").performClick()
        rule.onNodeWithText("Name (optional)").performTextReplacement("Renamed")
        rule.onNodeWithText("Project", useUnmergedTree = true).performClick()
        rule.onNodeWithText("Save").performClick()
        rule.runOnIdle { assertEquals(1L, pins.single().projectId); assertEquals("PROJECT", pins.single().scope) }
        rule.onNodeWithText("Renamed").performTouchInput { longClick() }
        rule.onNodeWithText("Delete action").performClick()
        rule.onNodeWithText("Pin recent input or tap New to create an action.").assertExists()
    }

    @Test fun recentStartsWith20AndCanShowOlderItems() {
        var prepared = ""
        rule.setContent {
            TerminalHubTheme {
                PinnedActionsSheet(1, emptyList(), (1..25).map { TextInputHistoryEntity(id = it.toLong(), projectId = 1, text = "input $it") },
                    initiallyRecent = true, historyEnabled = true, onDismiss = {}, onPrepare = { prepared = it }, onSend = {}, onSave = {}, onDeletePin = {}, onDeleteHistory = {})
            }
        }
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Show more"))
        rule.onNodeWithText("Show more").performClick()
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("input 25"))
        rule.onNodeWithText("input 25").performClick()
        rule.runOnIdle { assertEquals("input 25", prepared) }
    }

    @Test fun unnamedHistoryPinUsesInputAsNameAndDefaultsToGlobal() {
        var saved: PinnedActionEntity? = null
        rule.setContent {
            TerminalHubTheme {
                PinnedActionsSheet(1, emptyList(), listOf(TextInputHistoryEntity(id = 1, projectId = 1, text = "git status")),
                    initiallyRecent = true, historyEnabled = true, onDismiss = {}, onPrepare = {}, onSend = {}, onSave = { saved = it }, onDeletePin = {}, onDeleteHistory = {})
            }
        }
        rule.onNodeWithText("git status").performTouchInput { longClick() }
        rule.onNodeWithText("Pin", useUnmergedTree = true).performClick()
        rule.onNodeWithText("Save").performClick()
        rule.runOnIdle {
            assertEquals("git status", saved?.name)
            assertEquals("GLOBAL", saved?.scope)
            assertNull(saved?.projectId)
        }
    }

    @Test fun newActionCanBeSavedWithoutNameButNotWithoutInput() {
        var saved: PinnedActionEntity? = null
        rule.setContent {
            TerminalHubTheme {
                PinnedActionsSheet(1, emptyList(), emptyList(), onDismiss = {}, onPrepare = {}, onSend = {},
                    onSave = { saved = it }, onDeletePin = {}, onDeleteHistory = {})
            }
        }
        rule.onNodeWithText("New").performClick()
        rule.onNodeWithText("Save").assertIsNotEnabled()
        rule.onNodeWithText("Input").performTextInput("run tests\nthen report")
        rule.onNodeWithText("Save").performClick()
        rule.runOnIdle {
            assertEquals("run tests", saved?.name)
            assertEquals("run tests\nthen report", saved?.text)
            assertEquals("GLOBAL", saved?.scope)
            assertNull(saved?.projectId)
        }
    }
}
