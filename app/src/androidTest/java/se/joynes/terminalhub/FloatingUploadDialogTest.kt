package se.joynes.terminalhub

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.*
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
import se.joynes.terminalhub.ui.screen.upload.*
import se.joynes.terminalhub.ui.theme.TerminalHubTheme

@HiltAndroidTest
class FloatingUploadDialogTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<HiltTestActivity>()
    private val uris = listOf("first.txt", "second.png", "third.pdf").map { Uri.parse("file:///tmp/$it") }
    private var selected by mutableStateOf<Uri?>(null)
    private var name by mutableStateOf("")
    private var state by mutableStateOf<UploadState>(UploadState.Uploading("first.txt", .25f))
    private var pickerResult = uris
    private var launches = 0
    private var dismissals = 0
    private val batches = mutableListOf<List<String>>()
    private val registry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(code: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            launches++
            assertEquals("*/*", input)
            val intent = Intent()
            if (pickerResult.isNotEmpty()) {
                intent.clipData = ClipData.newRawUri("files", pickerResult.first()).also { clip ->
                    pickerResult.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
                }
            }
            dispatchResult(code, if (pickerResult.isEmpty()) Activity.RESULT_CANCELED else Activity.RESULT_OK, intent)
        }
    }
    @Before fun setup() { hilt.inject() }
    private fun show(initial: Uri? = null) {
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides object : ActivityResultRegistryOwner {
                override val activityResultRegistry = registry
            }) {
                TerminalHubTheme {
                    FloatingFileUploadDialog(hiltViewModel(), Long.MAX_VALUE, Long.MAX_VALUE, state,
                        selected, name, { selected = it }, { name = it }, initial,
                        { batches += it }, { dismissals++ })
                }
            }
        }
    }
    @Test fun selectingMultipleFilesAdvancesInOrderAndReportsFullPathsOnlyAfterAllFinish() {
        show()
        compose.waitUntil(5000) { selected == uris[0] }
        compose.onNodeWithText("UPLOADING (1/3)...").assertIsDisplayed()
        compose.runOnIdle { state = UploadState.Done("first.txt", "/srv/project/first.txt") }
        compose.waitUntil(5000) { selected == uris[1] }
        compose.runOnIdle { assertTrue(batches.isEmpty()); state = UploadState.Uploading("second.png", .5f) }
        compose.onNodeWithText("UPLOADING (2/3)...").assertIsDisplayed()
        compose.runOnIdle { state = UploadState.Done("second.png", "/srv/project/second.png") }
        compose.waitUntil(5000) { selected == uris[2] }
        compose.runOnIdle { assertTrue(batches.isEmpty()); state = UploadState.Uploading("third.pdf", .75f) }
        compose.onNodeWithText("UPLOADING (3/3)...").assertIsDisplayed()
        compose.runOnIdle { state = UploadState.Done("third.pdf", "/srv/project/third.pdf") }
        compose.waitUntil(5000) { batches.size == 1 }
        assertEquals(listOf("/srv/project/first.txt", "/srv/project/second.png", "/srv/project/third.pdf"), batches.single())
        compose.onNodeWithText("DONE ✓  3 files uploaded").assertIsDisplayed()
        compose.onNodeWithText("CLOSE").performClick()
        compose.runOnIdle { assertEquals(1, launches); assertEquals(1, dismissals) }
    }
    @Test fun cancellingInitialPickerDismissesTheEmptyPanel() {
        pickerResult = emptyList(); show()
        compose.waitUntil(5000) { dismissals == 1 }
        compose.runOnIdle { assertNull(selected); assertTrue(batches.isEmpty()) }
    }
    @Test fun sharedFileSkipsPickerAndCopyUsesItsCompleteRemotePath() {
        val path = "/srv/my project/first.txt"
        show(uris.first())
        compose.waitUntil(5000) { selected == uris.first() }
        compose.runOnIdle { assertEquals("first.txt", name); assertEquals(0, launches); state = UploadState.Done("first.txt", path) }
        compose.onNodeWithText(path).assertIsDisplayed()
        compose.onNodeWithText("COPY PATH").performClick()
        compose.runOnIdle {
            val clipboard = compose.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            assertEquals(path, clipboard.primaryClip!!.getItemAt(0).text.toString())
        }
        compose.waitUntil(5000) { dismissals == 1 }
        assertEquals(listOf(listOf(path)), batches)
    }
    @Test fun failedTransferOffersPickingAnotherFileAndCloseRemainsAvailable() {
        selected = uris.first(); name = "first.txt"; state = UploadState.Error("Connection closed")
        pickerResult = listOf(uris[1]); show()
        compose.onNodeWithText("ERROR: Connection closed").assertIsDisplayed()
        compose.onNodeWithText("CHOOSE FILES").performClick()
        compose.waitUntil(5000) { selected == uris[1] }
        compose.onNodeWithText("✕").performClick()
        compose.runOnIdle { assertEquals(1, launches); assertEquals(1, dismissals) }
    }
    @Test fun alreadySelectedIdleFileCanBeReplacedWithoutAutoOpeningPicker() {
        selected = uris.first(); name = "first.txt"; state = UploadState.Idle
        pickerResult = listOf(uris[2]); show()
        compose.runOnIdle { assertEquals(0, launches) }
        compose.onNodeWithText("CHOOSE DIFFERENT FILES").performClick()
        compose.waitUntil(5000) { selected == uris[2] }
        assertEquals("third.pdf", name)
    }
}
