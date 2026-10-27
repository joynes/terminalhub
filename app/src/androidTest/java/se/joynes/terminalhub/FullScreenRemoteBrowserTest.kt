package se.joynes.terminalhub

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import se.joynes.terminalhub.data.settings.RemoteBrowseViewState
import se.joynes.terminalhub.data.ssh.RemoteFileEntry
import se.joynes.terminalhub.ui.screen.download.*
import se.joynes.terminalhub.ui.theme.TerminalHubTheme

class FullScreenRemoteBrowserTest {
    @get:Rule val compose = createComposeRule()
    private var directory by mutableStateOf("")
    private var preview by mutableStateOf<RemoteFileEntry?>(null)
    private var closed = false
    private var downloads = 0
    private var refreshes = 0
    private val saved = mutableMapOf<String, RemoteBrowseViewState>()
    private val files = listOf(RemoteFileEntry("documents", 0, true), RemoteFileEntry("images", 0, true),
        RemoteFileEntry("README.md", 1024), RemoteFileEntry("report.pdf", 2048), RemoteFileEntry("photo.png", 4096)) +
        (1..70).map { RemoteFileEntry("note-${it.toString().padStart(2, '0')}.txt", 150, modifiedAtEpochSeconds = it.toLong()) }

    private fun mount(initial: String = "") {
        directory = initial
        compose.setContent {
            TerminalHubTheme {
                var sort by remember { mutableStateOf(RemoteFileSortSelection(RemoteFileSort.NAME, true)) }
                val entries = sortRemoteFileEntries(files, sort)
                val currentDirectory = directory
                val browser = rememberRemoteBrowserUiState("fixture", directory, entries,
                    load = { saved[currentDirectory] ?: RemoteBrowseViewState() }, save = { saved[currentDirectory] = it })
                val selected = preview
                if (selected != null) RemoteTextPreviewDialog(selected.name, "# Preview\nText content", true) { preview = null }
                else {
                    RemoteBrowserFrame("Remote files", onBack = {
                        if (directory.isEmpty()) closed = true else directory = parentRemoteDirectory(directory)
                    }, onClose = { closed = true }) {
                        RemoteBrowserListing(directory, entries, browser, sort, { sort = nextRemoteFileSortSelection(sort, it) },
                            { directory = it }, { refreshes++ }, { preview = it }, "Save to: Downloads", null, false,
                            {}, { downloads++ })
                    }
                }
            }
        }
    }

    @Test fun portraitNavigationUsesAncestorsUpAndProjectRoot() {
        mount("documents/reports/2026")
        compose.onNodeWithContentDescription("Up one level").performClick()
        compose.runOnIdle { assertEquals("documents/reports", directory) }
        compose.onNodeWithTag("breadcrumb:documents").performClick()
        compose.runOnIdle { assertEquals("documents", directory) }
        compose.onNodeWithContentDescription("Project folder").performClick()
        compose.onNodeWithText("documents").performClick()
        compose.runOnIdle { assertEquals("documents", directory) }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle { assertEquals("", directory); assertFalse(closed) }
        compose.onNodeWithContentDescription("Up one level").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle { assertTrue(closed) }
    }

    @Test fun oneTapPreviewReturnsToSameScrollAndCheckboxSelectsWithoutOpeningPreview() {
        mount()
        compose.onNodeWithTag("remote-file-list").performScrollToNode(hasText("note-50.txt"))
        compose.onNodeWithContentDescription("Select note-50.txt").performClick()
        compose.runOnIdle { assertNull(preview); assertEquals(0, downloads) }
        compose.onNodeWithText("note-50.txt").performClick()
        compose.onNodeWithTag("remote-text-preview").assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("note-50.txt").assertIsDisplayed()
        compose.onNodeWithContentDescription("Select note-50.txt").assertIsOn()
        compose.onNodeWithText("Change folder").assertIsDisplayed()
        compose.onNodeWithTag("download-selected").performClick()
        compose.runOnIdle { assertEquals(1, downloads) }
    }

    @Test fun searchSortAndFixedControlsWorkAfterLongScroll() {
        mount()
        compose.onNodeWithContentDescription("Search this folder").performClick()
        compose.onNodeWithText("Filter names in this folder").performTextInput("report")
        compose.onNodeWithText("report.pdf").assertIsDisplayed()
        compose.onNodeWithText("README.md").assertDoesNotExist()
        compose.onNodeWithText("report.pdf").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("report.pdf").assertIsDisplayed()
        compose.onNodeWithText("README.md").assertDoesNotExist()
        compose.onNodeWithContentDescription("Search this folder").performClick()
        compose.onNodeWithContentDescription("Sort files").performClick()
        compose.onNodeWithText("Date").performClick()
        compose.onNodeWithContentDescription("Sort files").performClick()
        compose.onNode(hasText("Date ↓") and hasClickAction() and !hasContentDescription("Sort files")).assertExists().performClick()
        compose.onNodeWithTag("remote-file-list").performScrollToNode(hasText("note-70.txt"))
        compose.onNodeWithContentDescription("Refresh files").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, refreshes) }
        // Keep an actual device image for visual review of the portrait layout.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        context.openFileOutput("remote-browser-portrait.png", 0).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        compose.onNodeWithText("Change folder").assertIsDisplayed()
    }
}
