package se.joynes.terminalhub

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import se.joynes.terminalhub.data.ssh.RemoteFileEntry
import se.joynes.terminalhub.ui.screen.download.*
import se.joynes.terminalhub.ui.theme.TerminalHubTheme

@HiltAndroidTest
class FloatingDownloadDialogTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<HiltTestActivity>()
    private var state by mutableStateOf<DownloadState>(DownloadState.LoadingList(""))
    private var closed = false
    private var folderRequests = 0
    private val opened = mutableListOf<Intent>()
    private lateinit var vm: FileDownloadViewModel
    private val files = listOf(RemoteFileEntry("first.txt", 100), RemoteFileEntry("second.bin", 200))
    private val registry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            assertTrue(contract is androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree)
            folderRequests++
            dispatchResult(requestCode, Activity.RESULT_CANCELED, Intent())
        }
    }
    @Before fun setup() { hilt.inject() }
    private fun show() {
        val context = object : ContextWrapper(compose.activity) {
            override fun startActivity(intent: Intent) { opened += intent }
        }
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context,
                LocalActivityResultRegistryOwner provides object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }) {
                TerminalHubTheme {
                    vm = hiltViewModel()
                    FloatingFileDownloadDialog(vm, Long.MAX_VALUE, Long.MAX_VALUE, state, { closed = true })
                }
            }
        }
    }
    @Test fun loadingTransferPauseAndPreviewProgressAreClearlyDifferentStates() {
        show()
        compose.onNodeWithText("Loading remote files…").assertIsDisplayed()
        compose.runOnIdle { state = DownloadState.Downloading("archive.zip", .4f, currentFile = 2, totalFiles = 3, queuedFiles = 1) }
        compose.onNodeWithText("FILE 2 OF 3: archive.zip").assertIsDisplayed()
        compose.onNodeWithText("1 file(s) waiting").assertIsDisplayed()
        compose.onNodeWithText("CANCEL QUEUE").assertIsEnabled()
        compose.runOnIdle { state = DownloadState.Paused("archive.zip", "Network lost", 1) }
        compose.onNodeWithText("TRANSFER PAUSED: archive.zip").assertIsDisplayed()
        compose.onNodeWithText("Network lost").assertIsDisplayed()
        compose.onNodeWithText("RETRY QUEUE").performClick()
        compose.onNodeWithText("CANCEL QUEUE").performClick()
        compose.runOnIdle { state = DownloadState.PreviewLoading("", files, "first.txt", .7f) }
        compose.onNodeWithText("PREPARING PREVIEW: first.txt").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close file browser").performClick()
        compose.runOnIdle { assertTrue(closed); assertEquals(0, folderRequests) }
    }
    @Test fun selectingFilesRequestsAFolderOnlyWhenDownloadIsRequestedAndCancelKeepsSelection() {
        state = DownloadState.Listed("", files); show()
        compose.onNodeWithContentDescription("Select first.txt").performClick()
        compose.onNodeWithContentDescription("Select second.bin").performClick()
        compose.runOnIdle { assertEquals(0, folderRequests) }
        compose.onNodeWithTag("download-selected").performClick()
        compose.waitUntil(5000) { folderRequests == 1 }
        compose.onNodeWithContentDescription("Select first.txt").assertIsOn()
        compose.onNodeWithContentDescription("Select second.bin").assertIsOn()
        compose.onNodeWithText("Change folder").performClick()
        compose.waitUntil(5000) { folderRequests == 2 }
        compose.onNodeWithContentDescription("Close file browser").performClick()
        compose.runOnIdle { assertTrue(closed) }
    }
    @Test fun completedFileOpensWithDetectedMimeTypeAndReadPermissionThroughAChooser() {
        val uri = Uri.parse("file:///tmp/readme.md")
        state = DownloadState.BatchDone(listOf(DownloadedRemoteFile("readme.md", 123, uri))); show()
        compose.onNodeWithText("DONE - 1 FILE(S)").assertIsDisplayed()
        compose.onNodeWithText("OPEN").performClick()
        compose.runOnIdle {
            val chooser = opened.single()
            assertEquals(Intent.ACTION_CHOOSER, chooser.action)
            @Suppress("DEPRECATION") val view = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
            assertEquals(Intent.ACTION_VIEW, view.action); assertEquals(uri, view.data); assertEquals("text/plain", view.type)
            assertTrue(view.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertEquals(uri, view.clipData!!.getItemAt(0).uri)
        }
        compose.onNodeWithText("CLOSE").performClick()
        compose.runOnIdle { assertTrue(closed) }
    }
    @Test fun listingFailureAllowsRetryAndRootWhilePreviewFailureOffersBackToFiles() {
        state = DownloadState.Error("Disconnected", "subfolder"); show()
        compose.onNodeWithText("ERROR: Disconnected").assertIsDisplayed()
        compose.onNodeWithText("TRY AGAIN").performClick()
        compose.waitUntil(5000) { vm.activities.value[Long.MAX_VALUE] is DownloadState.Error }
        compose.onNodeWithText("PROJECT ROOT").performClick()
        compose.runOnIdle { state = DownloadState.Error("Unsupported preview", "subfolder", DownloadState.Listed("subfolder", files)) }
        compose.onNodeWithText("BACK TO FILES").performClick()
        compose.onNodeWithContentDescription("Close file browser").performClick()
        compose.runOnIdle { assertTrue(closed) }
    }
    @Test fun textPreviewRendersWithoutOpeningADownloadPicker() {
        state = DownloadState.PreviewReady("", files, "first.txt", "Preview content", false); show()
        compose.onNodeWithTag("remote-text-preview").assertExists()
        compose.onNodeWithText("Preview content").assertExists()
        compose.runOnIdle { assertEquals(0, folderRequests); assertTrue(opened.isEmpty()) }
    }
}
