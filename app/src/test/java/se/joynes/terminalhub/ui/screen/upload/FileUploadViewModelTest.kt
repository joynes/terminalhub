package se.joynes.terminalhub.ui.screen.upload

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import java.io.ByteArrayInputStream
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import se.joynes.terminalhub.data.model.*
import se.joynes.terminalhub.data.repository.*
import se.joynes.terminalhub.data.security.SecurePrefsManager
import se.joynes.terminalhub.data.ssh.*
import se.joynes.terminalhub.domain.ScriptTemplateEngine

@OptIn(ExperimentalCoroutinesApi::class)
class FileUploadViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val servers: ServerRepository = mock()
    private val projects: ProjectRepository = mock()
    private val templates = ScriptTemplateEngine()
    private val uploader: ScpUploader = mock()
    private val credentials: SecurePrefsManager = mock()
    private val context: Context = mock()
    private val resolver: ContentResolver = mock()
    private val uri: Uri = mock()
    private val cursor: Cursor = mock()
    private val server = Server(id = 1, name = "test", host = "fixture", username = "user", projectsFolder = "/projects")
    private val project = Project(id = 2, serverId = 1, name = "demo")
    private lateinit var vm: FileUploadViewModel

    @Before fun setup() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        whenever(servers.getById(1)).thenReturn(server)
        whenever(projects.getById(2)).thenReturn(project)
        whenever(context.contentResolver).thenReturn(resolver)
        whenever(resolver.query(eq(uri), isNull(), isNull(), isNull(), isNull())).thenReturn(cursor)
        whenever(cursor.moveToFirst()).thenReturn(true)
        whenever(cursor.getColumnIndex("_display_name")).thenReturn(0)
        whenever(cursor.getColumnIndex("_size")).thenReturn(1)
        whenever(cursor.getString(0)).thenReturn("report.pdf")
        whenever(cursor.getLong(1)).thenReturn(4)
        whenever(resolver.openInputStream(uri)).thenReturn(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)))
        whenever(credentials.getPassword(1)).thenReturn("password")
        whenever(uploader.upload(any(), anyOrNull(), anyOrNull(), any(), any(), any(), any())).thenReturn(emptyFlow())
        vm = FileUploadViewModel(servers, projects, templates, uploader, credentials)
    }
    @After fun cleanup() { Dispatchers.resetMain() }

    @Test fun successfulUploadUsesProviderMetadataAndReturnsFullProjectPath() = runTest(dispatcher) {
        vm.startUpload(1, 2, uri, context); runCurrent()
        assertEquals(UploadState.Done("report.pdf", "/projects/demo/report.pdf"), vm.uploadState(2).first())
        verify(uploader).upload(eq(server), eq("password"), isNull(), eq("report.pdf"), eq(4), any(), eq("/projects/demo"))
        verify(cursor).close()
        assertSame(UploadState.Idle, vm.uploadState(99).first())
        vm.reset(2)
        assertSame(UploadState.Idle, vm.uploadState(2).first())
    }

    @Test fun missingServerAndProjectFailBeforeOpeningAFile() = runTest(dispatcher) {
        vm.startUpload(99, 2, uri, context); runCurrent()
        assertEquals(UploadState.Error("Server not found"), vm.uploadState(2).first())
        whenever(projects.getById(2)).thenReturn(null)
        vm.startUpload(1, 2, uri, context); runCurrent()
        assertEquals(UploadState.Error("Project not found"), vm.uploadState(2).first())
        verifyNoInteractions(resolver, uploader)
    }

    @Test fun missingInputStreamIsReportedAndMetadataCursorStillCloses() = runTest(dispatcher) {
        whenever(resolver.openInputStream(uri)).thenReturn(null)
        vm.startUpload(1, 2, uri, context); runCurrent()
        assertEquals(UploadState.Error("Cannot open file"), vm.uploadState(2).first())
        verify(cursor).close()
        verifyNoInteractions(uploader)
    }

    @Test fun absentProviderMetadataUsesSafeFallbacks() = runTest(dispatcher) {
        whenever(cursor.getColumnIndex(any())).thenReturn(-1)
        vm.startUpload(1, 2, uri, context); runCurrent()
        assertEquals(UploadState.Done("file", "/projects/demo/file"), vm.uploadState(2).first())
        verify(uploader).upload(any(), anyOrNull(), anyOrNull(), eq("file"), eq(0), any(), any())
    }

    @Test fun uploadFailureCannotBecomeSuccessOrChangeOtherProjectState() = runTest(dispatcher) {
        whenever(uploader.upload(any(), anyOrNull(), anyOrNull(), any(), any(), any(), any())).thenReturn(flow { throw IllegalStateException("Disk full") })
        vm.startUpload(1, 2, uri, context); runCurrent()
        assertEquals(UploadState.Error("Disk full"), vm.uploadState(2).first())
        assertSame(UploadState.Idle, vm.uploadState(3).first())
    }

    @Test fun duplicateRequestWhileUploadingDoesNotStartAnotherTransfer() = runTest(dispatcher) {
        val release = CompletableDeferred<Unit>()
        whenever(uploader.upload(any(), anyOrNull(), anyOrNull(), any(), any(), any(), any())).thenReturn(flow { release.await() })
        vm.startUpload(1, 2, uri, context); runCurrent()
        assertEquals(UploadState.Uploading("report.pdf", 0f), vm.uploadState(2).first())
        vm.startUpload(1, 2, uri, context); runCurrent()
        verify(uploader, times(1)).upload(any(), anyOrNull(), anyOrNull(), any(), any(), any(), any())
        release.complete(Unit); runCurrent()
        assertTrue(vm.uploadState(2).first() is UploadState.Done)
    }

    @Test fun doubleTapBeforeDispatchStartsOnlyOneTransfer() = runTest(dispatcher) {
        val release = CompletableDeferred<Unit>()
        whenever(uploader.upload(any(), anyOrNull(), anyOrNull(), any(), any(), any(), any())).thenReturn(flow { release.await() })
        vm.startUpload(1, 2, uri, context)
        vm.startUpload(1, 2, uri, context)
        runCurrent()
        verify(uploader, times(1)).upload(any(), anyOrNull(), anyOrNull(), any(), any(), any(), any())
        release.complete(Unit); runCurrent()
    }
}
