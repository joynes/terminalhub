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
import se.joynes.terminalhub.data.git.*
import se.joynes.terminalhub.ui.screen.git.*
import se.joynes.terminalhub.ui.screen.settings.KeyBarSettingsEditor
import se.joynes.terminalhub.ui.screen.terminal.MutableModifierManager
import se.joynes.terminalhub.ui.screen.terminal.SpecialKeyBar
import se.joynes.terminalhub.ui.theme.TerminalHubTheme

@RunWith(AndroidJUnit4::class)
class GitReviewUiTest {
    @get:Rule val compose = createComposeRule()
    private val file = GitChangedFile("src/sample.kt", "M")
    private fun state() = GitReviewState(projectName = "demo", repository = "/projects/demo", repositories = listOf("/projects/demo"),
        snapshot = GitReviewSnapshot("/projects/demo", "main", "abc123 · Example commit", listOf(file)))

    @Test fun fileOpensDiffAndCanReturnToFileList() {
        var current by mutableStateOf(state())
        compose.setContent { TerminalHubTheme {
            GitReviewDialog(current, {}, {}, { current = current.copy(file = it, patch = "@@ -1 +1 @@\n-val old = 1\n+val new = 2\n") },
                { current = current.copy(file = null, patch = null) }, {}, {})
        } }
        compose.onNodeWithText("src/sample.kt").performClick()
        compose.onNodeWithText("val new = 2").assertIsDisplayed()
        compose.onNodeWithText("val old = 1").assertIsDisplayed()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "git-review.png").outputStream().use {
            compose.onNodeWithTag("git-fullscreen").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithText("‹ FILES").performClick()
        compose.onNodeWithTag("git-file-list").assertExists()
    }

    @Test fun fullscreenDiffScrollsToEndAndBackAndKeepsLongCodeReadable() {
        val patch = "@@ -0,0 +1,240 @@\n" + (1..240).joinToString("\n") { "+val line$it = \"a long source line that wraps on a phone without losing its ending END$it\"" }
        compose.setContent { TerminalHubTheme {
            GitReviewDialog(state().copy(file = file, patch = patch), {}, {}, {}, {}, {}, {})
        } }
        compose.onNodeWithTag("git-fullscreen").assertExists()
        compose.onNodeWithTag("git-diff-scroll").performScrollToIndex(240)
        compose.onNodeWithText("END240", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("git-diff-scroll").performTouchInput { swipeDown() }
        compose.onNodeWithTag("git-diff-scroll").performScrollToIndex(1)
        compose.onNodeWithText("val line1 =", substring = true).assertIsDisplayed()
        compose.onNodeWithText("WINDOW").performClick()
        compose.onNodeWithTag("git-windowed").assertExists()
        compose.onNodeWithText("FULL SCREEN").performClick()
        compose.onNodeWithTag("git-fullscreen").assertExists()
        compose.onNodeWithText("A+").performClick()
        compose.onNodeWithText("A−").performClick()
    }

    @Test fun modesAndRefreshGiveImmediateLoadingFeedback() {
        var current by mutableStateOf(state())
        compose.setContent { TerminalHubTheme {
            GitReviewDialog(current, {}, { current = current.copy(mode = it, loading = true) }, {}, {},
                { current = current.copy(loading = true) }, {})
        } }
        compose.onNodeWithText("Staged", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("git-loading").assertIsDisplayed()
        compose.onNodeWithText(GitReviewMode.STAGED.explanation).assertIsDisplayed()
        compose.runOnIdle { current = current.copy(loading = false) }
        compose.onNodeWithText("REFRESH").performClick()
        compose.onNodeWithTag("git-loading").assertIsDisplayed()
    }

    @Test fun gitKeyOpensReviewWithoutSendingTerminalInput() {
        var opened = 0
        val inputs = mutableListOf<String>()
        compose.setContent { TerminalHubTheme {
            SpecialKeyBar(MutableModifierManager(), onKey = { inputs += it }, rows = listOf(listOf("GIT_REVIEW")), onGitReview = { opened++ })
        } }
        compose.onNodeWithContentDescription("Git changes").performClick()
        compose.runOnIdle { assertEquals(1, opened); assertTrue(inputs.isEmpty()) }
    }

    @Test fun keybarSettingsPickerOffersGitDiff() {
        var rows by mutableStateOf(listOf(listOf("ENTER")))
        compose.setContent { TerminalHubTheme {
            KeyBarSettingsEditor(rows, onRowsChange = { rows = it })
        } }
        compose.onNodeWithText("ENTER").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("GIT DIFF"))
        compose.onNodeWithText("GIT DIFF").performClick()
        compose.runOnIdle { assertEquals(listOf(listOf("GIT_REVIEW")), rows) }
        compose.onNodeWithText("GIT DIFF").assertExists()
    }

    @Test fun errorCanBeRetriedAndDialogClosed() {
        var retried = false
        var closed = false
        compose.setContent { TerminalHubTheme {
            GitReviewDialog(state().copy(error = "Server cannot be reached."), {}, {}, {}, {}, { retried = true }, { closed = true })
        } }
        compose.onNodeWithText("Server cannot be reached.").assertIsDisplayed()
        compose.onNodeWithText("RETRY").performClick()
        compose.onNodeWithText("CLOSE").performClick()
        compose.runOnIdle { assertTrue(retried); assertTrue(closed) }
    }
}
