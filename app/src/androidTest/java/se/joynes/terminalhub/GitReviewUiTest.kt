package se.joynes.terminalhub

import androidx.compose.runtime.*
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
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

    @Test fun repositoryChoiceEmptyCommitAndRetryStayInTheCurrentReviewContext() {
        var current by mutableStateOf(GitReviewState(projectName = "demo", loading = false, repositories = listOf("/one", "/two")))
        var retries = 0
        compose.setContent { TerminalHubTheme { GitReviewDialog(current, { current = state().copy(repository = it, repositories = listOf("/one", "/two")) },
            { current = current.copy(mode = it, snapshot = current.snapshot?.copy(files = emptyList())) },
            { retries++ }, {}, { retries++ }, {}) } }
        compose.onNodeWithText("Choose a repository").assertIsDisplayed()
        compose.onNodeWithText("/two").performClick()
        compose.onNodeWithText("one").performClick()
        compose.runOnIdle { assertEquals("/one", current.repository) }
        compose.onNodeWithText(GitReviewMode.COMMIT.label).performClick()
        compose.onNodeWithText("No file changes in this commit.").assertIsDisplayed()
        compose.onNodeWithText("abc123 · Example commit").assertIsDisplayed()
        compose.runOnIdle { current = current.copy(error = "Fixture server error") }
        compose.onNodeWithText("RETRY").performClick()
        compose.runOnIdle { assertEquals(1, retries); current = current.copy(file = file) }
        compose.onNodeWithText("RETRY").performClick()
        compose.runOnIdle { assertEquals(2, retries); current = current.copy(error = null, patch = "", truncated = true) }
        compose.onNodeWithText("Large diff: showing the beginning only.").assertExists()
        compose.onNodeWithText("No text differences. The file may have changed since the list was loaded.").assertExists()
    }

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
        hideTestKeyboard()
        val list = compose.onNodeWithTag("git-diff-scroll")
        list.performScrollToIndex(240)
        list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
        assertDiffTextVisible("END240")
        androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            .executeShellCommand("screencap -p /sdcard/Download/git-diff-end.png")
        list.performScrollToIndex(0)
        list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -100_000f) }
        assertDiffTextVisible("val line1 =")
        compose.onNodeWithText("WINDOW").performClick()
        compose.onNodeWithTag("git-windowed").assertExists()
        compose.onNodeWithText("FULL SCREEN").performClick()
        compose.onNodeWithTag("git-fullscreen").assertExists()
        compose.onNodeWithText("A+").performClick()
        compose.onNodeWithText("A−").performClick()
    }

    @Test fun windowedReviewKeepsPanelTapsInsideAndBackdropReturnsFromFileBeforeClosing() {
        var current by mutableStateOf(state().copy(file = file, patch = "+example"))
        var backs = 0; var dismissals = 0
        compose.setContent { TerminalHubTheme { GitReviewDialog(current, {}, {}, {},
            { backs++; current = current.copy(file = null, patch = null) }, {}, { dismissals++ }) } }
        compose.onNodeWithText("WINDOW").performClick()
        compose.onNodeWithText("Git · demo").performClick()
        compose.runOnIdle { assertEquals(0, backs); assertEquals(0, dismissals) }
        compose.onNodeWithTag("git-window-backdrop").performTouchInput { click(androidx.compose.ui.geometry.Offset(1f, 1f)) }
        compose.runOnIdle { assertEquals(1, backs); assertEquals(0, dismissals) }
        compose.onNodeWithTag("git-file-list").assertExists()
        compose.onNodeWithTag("git-window-backdrop").performTouchInput { click(androidx.compose.ui.geometry.Offset(1f, 1f)) }
        compose.runOnIdle { assertEquals(1, backs); assertEquals(1, dismissals) }
    }

    private fun assertDiffTextVisible(text: String) {
        val node = compose.onNodeWithText(text, substring = true)
        try {
            compose.waitUntil(5000) { runCatching {
                node.assertIsDisplayed()
                assertFalse("The whole wrapped code row must be visible above system bars", node.isNotDisplayed())
            }.isSuccess }
            node.assertIsDisplayed()
            assertFalse("The whole wrapped code row must be visible above system bars", node.isNotDisplayed())
        } catch (error: Throwable) {
            val semantic = node.fetchSemanticsNode()
            val details = buildString {
                appendLine("Git diff text: $text")
                appendLine("Text bounds: ${runCatching { node.fetchSemanticsNode().boundsInRoot }.getOrNull()}")
                appendLine("List bounds: ${compose.onNodeWithTag("git-diff-scroll").fetchSemanticsNode().boundsInRoot}")
                appendLine(compose.onNodeWithTag("git-diff-scroll").printToString())
                compose.runOnIdle {
                    val layout = semantic.javaClass.methods.firstOrNull { it.name.startsWith("getLayoutInfo") }
                        ?.invoke(semantic) as? androidx.compose.ui.layout.LayoutInfo
                    var parent = layout
                    while (parent != null) {
                        appendLine("Layout: ${parent.javaClass.name} placed=${parent.isPlaced} size=${parent.width}x${parent.height}")
                        parent = parent.parentInfo
                    }
                    fun describe(view: android.view.View) {
                        if (view.parent !is android.view.View || view.javaClass.simpleName.contains("Compose")) {
                            val rect = android.graphics.Rect()
                            val visible = view.getGlobalVisibleRect(rect)
                            val window = IntArray(2); view.getLocationInWindow(window)
                            val screen = IntArray(2); view.getLocationOnScreen(screen)
                            appendLine("View: ${view.javaClass.name} shown=${view.isShown} focus=${view.hasWindowFocus()} size=${view.width}x${view.height} visible=$visible rect=$rect window=${window.toList()} screen=${screen.toList()}")
                        }
                        if (view is android.view.ViewGroup) for (i in 0 until view.childCount) describe(view.getChildAt(i))
                    }
                    android.view.inspector.WindowInspector.getGlobalWindowViews().forEach(::describe)
                }
            }
            val screenshot = runCatching {
                androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                    .executeShellCommand("screencap -p /sdcard/Download/ci-git-scroll.png")
            }
            throw AssertionError("$details\nNative screenshot: $screenshot", error)
        }
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
