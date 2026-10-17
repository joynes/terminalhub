package se.joynes.terminalhub

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import se.joynes.terminalhub.data.settings.RemoteBrowseLocationStore
import se.joynes.terminalhub.data.settings.RemoteBrowseViewState
import se.joynes.terminalhub.data.ssh.RemoteFileEntry
import se.joynes.terminalhub.ui.screen.download.*
import se.joynes.terminalhub.ui.theme.TerminalHubTheme

class RemoteBrowserViewportUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = RemoteBrowseLocationStore(context)
    private val entries = (0..99).map { RemoteFileEntry("file-$it.txt", 10) }
    private val saved = RemoteBrowseViewState(20, 17, "false:file-20.txt", 43, setOf("file-22.txt", "gone.txt"))
    private var active: Fixture? = null

    private class Fixture(entries: List<RemoteFileEntry>) {
        var visible by mutableStateOf(true)
        var directory by mutableStateOf("images")
        var entries by mutableStateOf(entries)
        lateinit var state: RemoteBrowserUiState
        lateinit var scope: CoroutineScope
    }

    private fun mount(files: List<RemoteFileEntry> = entries): Fixture {
        context.getSharedPreferences("remote_browse_locations", Context.MODE_PRIVATE).edit().clear().commit()
        store.saveView("test-project", "images", saved)
        val fixture = Fixture(files)
        active = fixture
        compose.setContent {
            TerminalHubTheme {
                if (fixture.visible) {
                    val directory = fixture.directory
                    val state = rememberRemoteBrowserUiState("test-project", directory, fixture.entries,
                        load = { RemoteBrowseLocationStore(context).readView("test-project", directory) },
                        save = { RemoteBrowseLocationStore(context).saveView("test-project", directory, it) })
                    val scope = rememberCoroutineScope()
                    SideEffect { fixture.state = state; fixture.scope = scope }
                    Column(Modifier.height(340.dp).verticalScroll(state.panel)) {
                        Spacer(Modifier.height(80.dp))
                        RemoteFileList(fixture.entries, state.selectedFiles,
                            modifier = Modifier.height(180.dp).testTag("browser-files"), listState = state.list,
                            onOpenDirectory = {}, onToggleFile = {
                                state.selectedFiles = toggleRemoteFileSelection(state.selectedFiles, it.name)
                            })
                        Spacer(Modifier.height(300.dp))
                    }
                }
            }
        }
        compose.waitForIdle()
        return fixture
    }

    private fun reopen(fixture: Fixture) {
        compose.runOnIdle { fixture.visible = false }
        compose.waitForIdle()
        compose.runOnIdle { fixture.visible = true }
        compose.waitForIdle()
    }

    @After fun cleanup() {
        compose.runOnIdle { active?.visible = false }
        compose.waitForIdle()
        context.getSharedPreferences("remote_browse_locations", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun fingerScrollSelectionAndPanelScrollResumeExactlyAfterClosingAndRecreation() {
        val fixture = mount()
        compose.runOnIdle {
            assertEquals(20, fixture.state.list.firstVisibleItemIndex)
            assertEquals(17, fixture.state.list.firstVisibleItemScrollOffset)
            assertEquals(43, fixture.state.panel.value)
            assertEquals(setOf("file-22.txt"), fixture.state.selectedFiles)
        }
        compose.onNodeWithTag("browser-files").performTouchInput { swipeUp() }
        compose.waitForIdle()
        var visibleFile = ""
        compose.runOnIdle { visibleFile = fixture.entries[fixture.state.list.firstVisibleItemIndex + 1].name }
        compose.onNodeWithText(visibleFile).performClick()
        compose.runOnIdle { fixture.scope.launch { fixture.state.panel.scrollTo(61) } }
        compose.waitForIdle()
        lateinit var expected: RemoteBrowseViewState
        compose.runOnIdle {
            expected = fixture.state.snapshot(fixture.entries)
            assertTrue(expected.firstVisibleIndex > 20)
            assertEquals(61, expected.panelScroll)
            assertTrue(visibleFile in expected.selectedFiles)
        }
        reopen(fixture)
        compose.runOnIdle { assertEquals(expected, fixture.state.snapshot(fixture.entries)) }
        assertEquals(expected, RemoteBrowseLocationStore(context).readView("test-project", "images"))
    }

    @Test fun eachDirectoryHasItsOwnViewportWhenNavigatingBack() {
        val fixture = mount()
        store.saveView("test-project", "other", RemoteBrowseViewState(5, 11, "false:file-5.txt", 23))
        compose.runOnIdle { fixture.directory = "other" }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(5, fixture.state.list.firstVisibleItemIndex)
            assertEquals(11, fixture.state.list.firstVisibleItemScrollOffset)
            fixture.directory = "images"
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(20, fixture.state.list.firstVisibleItemIndex)
            assertEquals(17, fixture.state.list.firstVisibleItemScrollOffset)
            assertEquals(43, fixture.state.panel.value)
        }
    }

    @Test fun restoredListingFindsSameAnchorDespiteInsertedFilesAndChangedOrder() {
        val fixture = mount(listOf(RemoteFileEntry("new.txt", 1)) + entries.reversed())
        compose.runOnIdle {
            val snapshot = fixture.state.snapshot(fixture.entries)
            assertEquals("false:file-20.txt", snapshot.anchorKey)
            assertEquals(17, snapshot.firstVisibleOffset)
            assertFalse("gone.txt" in snapshot.selectedFiles)
        }
    }
}
