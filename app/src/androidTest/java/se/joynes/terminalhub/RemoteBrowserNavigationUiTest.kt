package se.joynes.terminalhub

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import se.joynes.terminalhub.data.model.Project
import se.joynes.terminalhub.data.model.Server
import se.joynes.terminalhub.data.settings.RemoteBrowseLocationStore
import se.joynes.terminalhub.data.ssh.RemoteFileEntry
import se.joynes.terminalhub.ui.screen.download.*
import se.joynes.terminalhub.ui.theme.TerminalHubTheme

class RemoteBrowserNavigationUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After fun clean() {
        context.getSharedPreferences("remote_browse_locations", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun entersSubfolderReopensThereAndRemembersNavigationBackToRoot() {
        clean()
        val server = Server(id = 7, name = "server", host = "example.com", username = "demo")
        val project = Project(id = 11, serverId = 7, name = "music")
        val root = "~/terminalhub/music"
        var visible by mutableStateOf(true)
        compose.setContent {
            TerminalHubTheme {
                if (visible) {
                    val store = remember { RemoteBrowseLocationStore(context) }
                    var directory by remember { mutableStateOf(store.lastDirectory(server, project, root) ?: "") }
                    Column {
                        RemoteDirectoryHeader(directory, onNavigateUp = {
                            directory = parentRemoteDirectory(directory)
                            store.save(server, project, root, directory)
                        })
                        RemoteFileList(
                            entries = if (directory.isEmpty()) listOf(RemoteFileEntry("images", 0, true))
                                else listOf(RemoteFileEntry("photo.png", 10)),
                            selectedFileNames = emptySet(), onToggleFile = {},
                            onOpenDirectory = {
                                directory = childRemoteDirectory(directory, it.name)
                                store.save(server, project, root, directory)
                            }
                        )
                    }
                }
            }
        }
        compose.onNodeWithText("[DIR]  images").performClick()
        compose.onNodeWithText("↑  PROJECT / images").assertExists()
        fun reopen() {
            compose.runOnIdle { visible = false }
            compose.waitForIdle()
            compose.runOnIdle { visible = true }
        }
        reopen()
        compose.onNodeWithText("photo.png").assertExists()
        compose.onNodeWithText("↑  PROJECT / images").performClick()
        reopen()
        compose.onNodeWithText("PROJECT /").assertExists()
        compose.onNodeWithText("[DIR]  images").assertExists()
        assertEquals("", RemoteBrowseLocationStore(context).lastDirectory(server, project, root))
    }
}
