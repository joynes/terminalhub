package se.joynes.terminalhub.ui.screen.download

import android.net.Uri
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.mock

class RemoteDownloadFolderControllerTest {
    private val folder: Uri = mock()
    private val request = PendingFolderDownload(7, 11, "images/nested", listOf("one.png", "two.txt"))
    private var saved: Uri? = folder
    private var writable = true
    private var starts = mutableListOf<Pair<PendingFolderDownload, Uri>>()
    private var choices = mutableListOf<Pair<PendingFolderDownload?, Uri?>>()
    private val controller = RemoteDownloadFolderController(
        remembered = { saved }, isWritable = { writable }, forget = { saved = null },
        start = { request, uri -> starts += request to uri }, choose = { request, uri -> choices += request to uri }
    )

    @Test fun authorizedFolderDownloadsWholeBatchDirectlyOnEveryUse() = runTest {
        repeat(2) { controller.download(request) }
        assertEquals(listOf(request to folder, request to folder), starts)
        assertTrue(choices.isEmpty())
        assertSame(folder, saved)
    }

    @Test fun firstDownloadAsksForFolderAndDoesNotStartBeforeSelection() = runTest {
        saved = null
        controller.download(request)
        assertEquals(listOf(request to null), choices)
        assertTrue(starts.isEmpty())
    }

    @Test fun missingFolderForgetsStaleGrantAndAsksAgainWithoutDownloading() = runTest {
        writable = false
        controller.download(request)
        assertNull(saved)
        assertEquals(listOf(request to folder), choices)
        assertTrue(starts.isEmpty())
    }

    @Test fun explicitFolderChangeNeverDownloadsAndCancelKeepsExistingFolder() {
        controller.changeFolder()
        assertEquals(listOf(null to folder), choices)
        assertTrue(starts.isEmpty())
        assertSame(folder, saved)
    }

    @Test fun pendingDownloadKeepsOriginalProjectDirectoryAndAllFileNames() = runTest {
        saved = null
        controller.download(request)
        assertEquals(7L, choices.single().first!!.serverId)
        assertEquals(11L, choices.single().first!!.projectId)
        assertEquals("images/nested", choices.single().first!!.directory)
        assertEquals(listOf("one.png", "two.txt"), choices.single().first!!.fileNames)
    }
}
