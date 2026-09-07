package se.joynes.terminalhub.ui.screen.download

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import se.joynes.terminalhub.data.model.Project
import se.joynes.terminalhub.data.model.ProjectTargetType
import se.joynes.terminalhub.data.model.Server
import se.joynes.terminalhub.data.repository.ProjectRepository
import se.joynes.terminalhub.data.repository.ServerRepository
import se.joynes.terminalhub.data.security.SecurePrefsManager
import se.joynes.terminalhub.data.ssh.RemoteFileEntry
import se.joynes.terminalhub.data.ssh.ScpDownloadProgress
import se.joynes.terminalhub.data.ssh.ScpDownloader
import se.joynes.terminalhub.domain.ScriptTemplateEngine
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class FileDownloadViewModelTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val serverRepo: ServerRepository = mock()
    private val projectRepo: ProjectRepository = mock()
    private val downloader: ScpDownloader = mock()
    private val securePrefs: SecurePrefsManager = mock()
    private val engine = ScriptTemplateEngine()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadRemoteFilesListsFilesFromActiveProjectFolder() = runTest(dispatcher) {
        val server = Server(
            id = 7L,
            name = "prod",
            host = "example.com",
            username = "demo",
            projectsFolder = "~/projects with spaces"
        )
        val project = Project(id = 11L, serverId = server.id, name = "midi-musik")
        val expected = listOf(RemoteFileEntry("README.md", 123L))
        whenever(serverRepo.getById(server.id)).thenReturn(server)
        whenever(projectRepo.getById(project.id)).thenReturn(project)
        whenever(
            downloader.listFiles(
                server = eq(server),
                password = isNull(),
                privateKeyPem = isNull(),
                remoteDir = eq("~/projects with spaces/midi-musik")
            )
        ).thenReturn(expected)
        val viewModel = createViewModel()

        viewModel.loadRemoteFiles(server.id, project.id)
        advanceUntilIdle()

        assertEquals(DownloadState.Listed("", expected), viewModel.downloadState(project.id).first())
        verify(downloader).listFiles(
            server = eq(server),
            password = isNull(),
            privateKeyPem = isNull(),
            remoteDir = eq("~/projects with spaces/midi-musik")
        )
    }

    @Test
    fun loadRemoteFilesListsSelectedSubdirectory() = runTest(dispatcher) {
        val server = Server(id = 7L, name = "prod", host = "example.com", username = "demo")
        val project = Project(id = 11L, serverId = server.id, name = "music")
        val expected = listOf(RemoteFileEntry("beat.wav", 456L))
        whenever(serverRepo.getById(server.id)).thenReturn(server)
        whenever(projectRepo.getById(project.id)).thenReturn(project)
        whenever(
            downloader.listFiles(
                server = eq(server),
                password = isNull(),
                privateKeyPem = isNull(),
                remoteDir = eq("~/terminalhub/music/stems/drums")
            )
        ).thenReturn(expected)
        val viewModel = createViewModel()

        viewModel.loadRemoteFiles(server.id, project.id, "stems/drums")
        advanceUntilIdle()

        assertEquals(
            DownloadState.Listed("stems/drums", expected),
            viewModel.downloadState(project.id).first()
        )
    }

    @Test
    fun loadRemoteFilesRejectsLocalProjectWithoutConnecting() = runTest(dispatcher) {
        val server = Server(id = 7L, name = "prod", host = "example.com", username = "demo")
        val project = Project(
            id = 11L,
            serverId = server.id,
            targetType = ProjectTargetType.LOCAL,
            name = "local-project"
        )
        whenever(serverRepo.getById(server.id)).thenReturn(server)
        whenever(projectRepo.getById(project.id)).thenReturn(project)
        val viewModel = createViewModel()

        viewModel.loadRemoteFiles(server.id, project.id)
        advanceUntilIdle()

        val state = viewModel.downloadState(project.id).first()
        assertTrue(state is DownloadState.Error)
        assertEquals("Remote download is only available for SSH projects", (state as DownloadState.Error).message)
    }

    @Test
    fun downloadUsesSelectedSubdirectoryBelowProjectRoot() = runTest(dispatcher) {
        val server = Server(id = 7L, name = "prod", host = "example.com", username = "demo")
        val project = Project(id = 11L, serverId = server.id, name = "music")
        val context: Context = mock()
        val resolver: ContentResolver = mock()
        val uri: Uri = mock()
        val output = ByteArrayOutputStream()
        whenever(serverRepo.getById(server.id)).thenReturn(server)
        whenever(projectRepo.getById(project.id)).thenReturn(project)
        whenever(context.contentResolver).thenReturn(resolver)
        whenever(context.cacheDir).thenReturn(temporaryFolder.root)
        whenever(resolver.openOutputStream(uri)).thenReturn(output)
        whenever(
            downloader.download(
                server = eq(server),
                password = isNull(),
                privateKeyPem = isNull(),
                remoteDir = eq("~/terminalhub/music/stems/drums"),
                fileName = eq("beat.wav"),
                outputStream = any()
            )
        ).thenAnswer { invocation ->
            val stagedOutput = invocation.getArgument<OutputStream>(5)
            flow {
                stagedOutput.write("complete file".toByteArray())
                emit(ScpDownloadProgress("beat.wav", 456L, 456L))
            }
        }
        val viewModel = createViewModel()

        viewModel.startDownload(server.id, project.id, "stems/drums", "beat.wav", uri, context)
        advanceUntilIdle()

        assertEquals(DownloadState.Done("beat.wav", 456L, uri), viewModel.downloadState(project.id).first())
        verify(downloader).download(
            server = eq(server),
            password = isNull(),
            privateKeyPem = isNull(),
            remoteDir = eq("~/terminalhub/music/stems/drums"),
            fileName = eq("beat.wav"),
            outputStream = any()
        )
        assertEquals("complete file", output.toString())
        assertTrue(temporaryFolder.root.resolve("downloads").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun failedNetworkDownloadDoesNotOpenOrPartiallyWriteDestination() = runTest(dispatcher) {
        val server = Server(id = 7L, name = "prod", host = "example.com", username = "demo")
        val project = Project(id = 11L, serverId = server.id, name = "music")
        val context: Context = mock()
        val resolver: ContentResolver = mock()
        val uri: Uri = mock()
        whenever(serverRepo.getById(server.id)).thenReturn(server)
        whenever(projectRepo.getById(project.id)).thenReturn(project)
        whenever(context.contentResolver).thenReturn(resolver)
        whenever(context.cacheDir).thenReturn(temporaryFolder.root)
        whenever(
            downloader.download(
                server = eq(server),
                password = isNull(),
                privateKeyPem = isNull(),
                remoteDir = eq("~/terminalhub/music"),
                fileName = eq("beat.wav"),
                outputStream = any()
            )
        ).thenReturn(flow { throw IOException("connection lost") })
        val viewModel = createViewModel()

        viewModel.startDownload(server.id, project.id, "", "beat.wav", uri, context)
        advanceUntilIdle()

        val state = viewModel.downloadState(project.id).first()
        assertTrue(state is DownloadState.Error)
        assertEquals("connection lost", (state as DownloadState.Error).message)
        verify(resolver, never()).openOutputStream(uri)
        assertTrue(temporaryFolder.root.resolve("downloads").listFiles().orEmpty().isEmpty())
    }

    private fun createViewModel() = FileDownloadViewModel(
        serverRepo = serverRepo,
        projectRepo = projectRepo,
        engine = engine,
        scpDownloader = downloader,
        securePrefs = securePrefs
    )
}
