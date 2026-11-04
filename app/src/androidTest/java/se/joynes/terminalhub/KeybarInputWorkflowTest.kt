package se.joynes.terminalhub

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import se.joynes.terminalhub.ui.screen.terminal.*
import se.joynes.terminalhub.ui.theme.TerminalHubTheme

class KeybarInputWorkflowTest {
    @get:Rule val compose = createComposeRule()
    @Test fun modifiersAreAppliedToOneKeyAndArrowsEncodeTheCorrectTerminalSequence() {
        val sent = mutableListOf<String>(); val manager = MutableModifierManager()
        var rows by mutableStateOf(listOf(listOf("CTRL", "ALT", "SHIFT", "CHAR_C"), listOf("UP", "DOWN", "LEFT", "RIGHT")))
        compose.setContent { TerminalHubTheme { SpecialKeyBar(manager, { sent += it }, rows = rows) } }
        compose.onNodeWithText("CTRL").performClick(); compose.onNodeWithText("C").performClick()
        compose.onNodeWithText("ALT").performClick(); compose.onNodeWithText("C").performClick()
        compose.onNodeWithText("⇧").performClick(); compose.onNodeWithText("C").performClick()
        compose.onNodeWithText("CTRL").performClick(); compose.onNodeWithText("↑").performClick()
        compose.onNodeWithText("ALT").performClick(); compose.onNodeWithText("↓").performClick()
        compose.onNodeWithText("⇧").performClick(); compose.onNodeWithText("←").performClick()
        compose.onNodeWithText("→").performClick()
        compose.runOnIdle { assertEquals(listOf("\u0003", "\u001Bc", "C", "\u001B[1;5A", "\u001B[1;3B", "\u001B[1;2D", "\u001B[C"), sent); assertFalse(manager.shift)
            rows = listOf(listOf("TAB", "ENTER", "BACKSPACE", "HOME", "END", "PAGE_UP", "PAGE_DOWN")) }
        for (label in listOf("⇥", "↵", "⌫", "HOME", "END", "PG↑", "PG↓")) compose.onNodeWithText(label).performClick()
        compose.runOnIdle { assertEquals(listOf("\t", "\r", "\u007F", "\u001B[H", "\u001B[F", "\u001B[5~", "\u001B[6~"), sent.takeLast(7)) }
    }
    @Test fun utilityButtonsCallTheirOwnActionsAndSwipeNavigatesProjects() {
        var previous = 0; var next = 0; val actions = mutableListOf<String>()
        compose.setContent { TerminalHubTheme { SpecialKeyBar(MutableModifierManager(), { error("Utility must not type bytes") },
            rows = listOf(listOf("PASTE", "PROJECT_NOTES", "GIT_REVIEW", "UPLOAD", "DOWNLOAD", "KEYBOARD")),
            onPaste = { actions += "paste" }, onProjectNotes = { actions += "notes" }, onGitReview = { actions += "git" },
            onFileUpload = { actions += "upload" }, onFileDownload = { actions += "download" }, onKeyboardToggle = { actions += "keyboard" },
            onPrevTab = { previous++ }, onNextTab = { next++ }) } }
        compose.onNodeWithText("PASTE").performClick()
        for (label in listOf("Project notes", "Git changes", "Upload files", "Browse remote files", "Toggle keyboard")) compose.onNodeWithContentDescription(label).performClick()
        compose.onRoot().performTouchInput {
            swipe(Offset(width * .85f, 20f), Offset(width * .15f, 20f), 500)
            swipe(Offset(width * .15f, 20f), Offset(width * .85f, 20f), 500)
        }
        compose.runOnIdle {
            assertEquals(listOf("paste", "notes", "git", "upload", "download", "keyboard"), actions)
            assertEquals(1, next); assertEquals(1, previous)
        }
    }
}
