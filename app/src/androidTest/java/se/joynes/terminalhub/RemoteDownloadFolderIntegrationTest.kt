package se.joynes.terminalhub

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import se.joynes.terminalhub.ui.screen.download.*
import java.util.regex.Pattern

class RemoteDownloadFolderIntegrationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun actualSafGrantSurvivesStoreRecreationAndRepeatedDownloadsDoNotOpenPicker() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = RemoteDownloadDirectoryStore(context)
        store.clear()
        var chosen: Uri? = null
        compose.setContent {
            val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                if (uri != null) {
                    context.contentResolver.takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    store.save(uri)
                    chosen = uri
                }
            }
            Button(onClick = { picker.launch(Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADocuments")) }) {
                Text("Choose test download folder")
            }
        }
        compose.onNodeWithText("Choose test download folder").performClick()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val useFolder = device.wait(Until.findObject(By.text(Pattern.compile("USE THIS FOLDER", Pattern.CASE_INSENSITIVE)).enabled(true)), 15_000)
        assertNotNull("Android folder chooser should show USE THIS FOLDER", useFolder)
        useFolder!!.click()
        device.wait(Until.findObject(By.text(Pattern.compile("ALLOW", Pattern.CASE_INSENSITIVE))), 5_000)?.click()
        compose.waitUntil(10_000) { chosen != null }
        val folder = chosen!!
        val recreated = RemoteDownloadDirectoryStore(context)
        assertEquals(folder, recreated.lastPersistedDirectory())
        assertTrue(recreated.isWritableDirectory(folder))
        var pickerCalls = 0
        val written = mutableListOf<Uri>()
        try {
            val controller = RemoteDownloadFolderController(
                remembered = recreated::lastPersistedDirectory,
                isWritable = recreated::isWritableDirectory,
                forget = recreated::clear,
                start = { request, uri ->
                    request.fileNames.forEach { name ->
                        val outputUri = createDownloadDocument(context, uri, name)
                        written += outputUri
                        context.contentResolver.openOutputStream(outputUri)!!.use { it.write("test".toByteArray()) }
                    }
                },
                choose = { _, _ -> pickerCalls++ }
            )
            runBlocking {
                repeat(2) { controller.download(PendingFolderDownload(1, 2, "nested", listOf("terminalhub-test-${System.nanoTime()}.txt"))) }
            }
            assertEquals(2, written.size)
            assertEquals(0, pickerCalls)
            written.forEach { uri ->
                assertEquals("test", context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() })
            }
            controller.changeFolder()
            assertEquals(1, pickerCalls)
            assertEquals(2, written.size)
            assertEquals(folder, recreated.lastPersistedDirectory())
            written.forEach { DocumentsContract.deleteDocument(context.contentResolver, it) }
            written.clear()
            context.contentResolver.releasePersistableUriPermission(folder,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            assertNull(RemoteDownloadDirectoryStore(context).lastPersistedDirectory())
        } finally {
            written.forEach { DocumentsContract.deleteDocument(context.contentResolver, it) }
            store.clear()
        }
    }
}
