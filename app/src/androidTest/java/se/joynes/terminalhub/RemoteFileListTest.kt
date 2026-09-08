package se.joynes.terminalhub

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.joynes.terminalhub.data.ssh.RemoteFileEntry
import se.joynes.terminalhub.ui.screen.download.RemoteFileList
import se.joynes.terminalhub.ui.screen.download.toggleRemoteFileSelection
import se.joynes.terminalhub.ui.theme.TerminalHubTheme
import androidx.compose.foundation.layout.height

@RunWith(AndroidJUnit4::class)
class RemoteFileListTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun longListScrollsAndAllowsSeveralFilesToRemainSelected() {
        val entries = (1..40).map { RemoteFileEntry("file-$it.txt", it.toLong()) }
        var selected by mutableStateOf<Set<String>>(emptySet())
        composeRule.setContent {
            TerminalHubTheme {
                RemoteFileList(
                    entries = entries,
                    selectedFileNames = selected,
                    modifier = Modifier.height(180.dp).testTag("remote-file-list"),
                    onOpenDirectory = {},
                    onToggleFile = { selected = toggleRemoteFileSelection(selected, it.name) }
                )
            }
        }

        composeRule.onNodeWithText("file-1.txt").performClick()
        composeRule.onNodeWithTag("remote-file-list").performScrollToNode(hasText("file-40.txt"))
        composeRule.onNodeWithText("file-40.txt").performClick()

        composeRule.runOnIdle {
            assertEquals(setOf("file-1.txt", "file-40.txt"), selected)
        }
    }
}
