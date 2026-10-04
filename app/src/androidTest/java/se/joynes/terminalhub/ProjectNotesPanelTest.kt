package se.joynes.terminalhub

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import android.graphics.Bitmap
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import se.joynes.terminalhub.data.db.entity.ProjectNoteEntity
import se.joynes.terminalhub.data.notes.*
import se.joynes.terminalhub.ui.screen.sessions.ProjectNotesPanel
import se.joynes.terminalhub.ui.screen.terminal.*
import se.joynes.terminalhub.ui.theme.TerminalHubTheme

class ProjectNotesPanelTest {
    @get:Rule val rule = createComposeRule()

    @Test fun noteTogglesWithoutTerminalBytesAndSwitchingProjectsUpdatesHeaderAndContent() {
        var visible by mutableStateOf(false)
        var id by mutableStateOf(1L)
        val notes = mutableStateMapOf(1L to "first", 2L to "second")
        rule.setContent {
            TerminalHubTheme {
                Column(Modifier.fillMaxSize().imePadding()) {
                    Box(Modifier.weight(1f)) {
                        if (visible) key(id) {
                            ProjectNotesPanel("project-$id", ProjectNoteState(ProjectNoteEntity(id, notes[id].orEmpty()), true, NoteSyncStatus.SYNCED),
                                onEdit = { notes[id] = it }, onClear = { notes[id] = "" }, onRetry = {}, onResolve = {}, onClose = { visible = false })
                        }
                    }
                    SpecialKeyBar(MutableModifierManager(), onKey = { error("NOTE must not send terminal bytes") }, onProjectNotes = { visible = !visible })
                }
            }
        }
        rule.onNodeWithContentDescription("Project notes").performClick()
        rule.onNodeWithText("NOTES · project-1").assertExists()
        rule.onNodeWithText("first").performTextReplacement("edited first")
        rule.runOnIdle { id = 2 }
        rule.onNodeWithText("NOTES · project-2").assertExists()
        rule.onNodeWithText("second").assertExists()
        val noteBounds = rule.onNodeWithText("NOTES · project-2").fetchSemanticsNode().boundsInRoot
        val keyBounds = rule.onNodeWithContentDescription("Project notes").fetchSemanticsNode().boundsInRoot
        assertTrue(noteBounds.bottom < keyBounds.top)
        val screenshot = rule.onRoot().captureToImage().asAndroidBitmap()
        File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "notes-panel.png").outputStream().use {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        rule.onNodeWithText("edited first").assertDoesNotExist()
        rule.onNodeWithContentDescription("Project notes").performClick()
        rule.onNodeWithText("NOTES · project-2").assertDoesNotExist()
        rule.runOnIdle { assertEquals("edited first", notes[1]) }
    }

    @Test fun emptyNoteShowsPlaceholderAndPendingClearIsHonest() {
        rule.setContent {
            TerminalHubTheme {
                ProjectNotesPanel("empty", ProjectNoteState(ProjectNoteEntity(1, pendingDelete = true), true, NoteSyncStatus.OFFLINE_PENDING),
                    onEdit = {}, onClear = {}, onRetry = {}, onResolve = {}, onClose = {})
            }
        }
        rule.onNodeWithText("Write plans, reminders or ideas…").assertExists()
        rule.onNodeWithText("Offline · pending sync").assertExists()
        rule.onNodeWithText("Clear pending · server copy will be removed after sync").assertExists()
    }

    @Test fun clearRequiresConfirmationAndConflictChoicesAreExplicit() {
        var cleared = false
        var keepDevice: Boolean? = null
        rule.setContent {
            TerminalHubTheme {
                ProjectNotesPanel("demo", ProjectNoteState(ProjectNoteEntity(1, "device text"), true, NoteSyncStatus.CONFLICT,
                    RemoteNote("key", "server text", 1)), onEdit = {}, onClear = { cleared = true }, onRetry = {},
                    onResolve = { keepDevice = it }, onClose = {})
            }
        }
        rule.onNodeWithText("CLEAR").performClick()
        rule.runOnIdle { assertFalse(cleared) }
        rule.onNodeWithText("CANCEL").performClick()
        rule.onNodeWithText("CLEAR").performClick()
        rule.onNodeWithText("CLEAR NOTE").performClick()
        rule.runOnIdle { assertTrue(cleared) }
        rule.onNodeWithText("VIEW BOTH").performScrollTo().performClick()
        rule.onNodeWithText("SERVER\nserver text").performScrollTo().assertExists()
        rule.runOnIdle { assertNull(keepDevice) }
        rule.onNodeWithText("KEEP THIS DEVICE").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(true, keepDevice) }
    }
}
