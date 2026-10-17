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
import kotlinx.coroutines.test.runCurrent
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
import org.mockito.kotlin.doSuspendableAnswer
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
    private val settingsRepository: se.joynes.terminalhub.data.settings.AppSettingsRepository = mock()
    private val browseLocationStore: se.joynes.terminalhub.data.settings.RemoteBrowseLocationStore = mock()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        whenever(settingsRepository.settings).thenReturn(kotlinx.coroutines.flow.MutableStateFlow(se.joynes.terminalhub.data.settings.AppSettings()))
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
        verify(browseLocationStore).save(server, project, "~/terminalhub/music", "stems/drums")
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

    @Test
    fun previewDownloadsMarkdownToMemoryWithoutCreatingADownloadDestination() = runTest(dispatcher) {
        val server = Server(id = 7L, name = "prod", host = "example.com", username = "demo")
        val project = Project(id = 11L, serverId = server.id, name = "music")
        val entry = RemoteFileEntry("README.md", 27L)
        val content = "# Project\n\nRemote preview"
        whenever(serverRepo.getById(server.id)).thenReturn(server)
        whenever(projectRepo.getById(project.id)).thenReturn(project)
        whenever(
            downloader.listFiles(
                server = eq(server),
                password = isNull(),
                privateKeyPem = isNull(),
                remoteDir = eq("~/terminalhub/music/docs")
            )
        ).thenReturn(listOf(entry))
        whenever(
            downloader.download(
                server = eq(server),
                password = isNull(),
                privateKeyPem = isNull(),
                remoteDir = eq("~/terminalhub/music/docs"),
                fileName = eq(entry.name),
                outputStream = any()
            )
        ).thenAnswer { invocation ->
            val output = invocation.getArgument<OutputStream>(5)
            flow {
                output.write(content.toByteArray())
                emit(ScpDownloadProgress(entry.name, content.length.toLong(), content.length.toLong()))
            }
        }
        val viewModel = createViewModel()

        viewModel.loadRemoteFiles(server.id, project.id, "docs")
        advanceUntilIdle()
        viewModel.previewRemoteFile(server.id, project.id, entry)
        advanceUntilIdle()

        assertEquals(
            DownloadState.PreviewReady(
                directory = "docs",
                entries = listOf(entry),
                fileName = entry.name,
                content = content,
                markdown = true
            ),
            viewModel.downloadState(project.id).first()
        )
    }

    @Test
    fun previewRejectsOversizedTextFileBeforeDownloading() = runTest(dispatcher) {
        val server = Server(id = 7L, name = "prod", host = "example.com", username = "demo")
        val project = Project(id = 11L, serverId = server.id, name = "music")
        val entry = RemoteFileEntry("huge.txt", MAX_REMOTE_TEXT_PREVIEW_BYTES + 1)
        whenever(serverRepo.getById(server.id)).thenReturn(server)
        whenever(projectRepo.getById(project.id)).thenReturn(project)
        whenever(
            downloader.listFiles(
                server = eq(server),
                password = isNull(),
                privateKeyPem = isNull(),
                remoteDir = eq("~/terminalhub/music")
            )
        ).thenReturn(listOf(entry))
        val viewModel = createViewModel()

        viewModel.loadRemoteFiles(server.id, project.id)
        advanceUntilIdle()
        viewModel.previewRemoteFile(server.id, project.id, entry)

        val state = viewModel.downloadState(project.id).first()
        assertTrue(state is DownloadState.Error)
        assertTrue((state as DownloadState.Error).message.contains("512 KB"))
        verify(downloader, never()).download(
            server = any(),
            password = any(),
            privateKeyPem = any(),
            remoteDir = any(),
            fileName = any(),
            outputStream = any()
        )
    }

    @Test fun imagePreviewPreservesBinaryBytesInMemoryAndReturnsToSameFolder() = runTest(dispatcher) {
        val server = Server(id = 7L, name = "prod", host = "example.com", username = "demo")
        val project = Project(id = 11L, serverId = server.id, name = "music")
        val bytes = byteArrayOf(0, 1, 2, -1, 0)
        val entry = RemoteFileEntry("cover.PNG", bytes.size.toLong())
        whenever(serverRepo.getById(server.id)).thenReturn(server)
        whenever(projectRepo.getById(project.id)).thenReturn(project)
        whenever(downloader.listFiles(eq(server), isNull(), isNull(), eq("~/terminalhub/music/images")))
            .thenReturn(listOf(entry))
        whenever(downloader.download(eq(server), isNull(), isNull(), eq("~/terminalhub/music/images"), eq(entry.name), any()))
            .thenAnswer { invocation ->
                val output = invocation.getArgument<OutputStream>(5)
                flow { output.write(bytes); emit(ScpDownloadProgress(entry.name, bytes.size.toLong(), bytes.size.toLong())) }
            }
        val viewModel = createViewModel()
        viewModel.loadRemoteFiles(server.id, project.id, "images")
        advanceUntilIdle()
        viewModel.previewRemoteFile(server.id, project.id, entry)
        advanceUntilIdle()
        val preview = viewModel.downloadState(project.id).first() as DownloadState.PreviewReady
        org.junit.Assert.assertArrayEquals(bytes, preview.imageBytes)
        assertEquals("", preview.content)
        assertEquals(false, preview.markdown)
        viewModel.closePreview(project.id)
        assertEquals(DownloadState.Listed("images", listOf(entry)), viewModel.downloadState(project.id).first())
    }

    @Test fun imageTransferIsBoundedEvenIfRemoteSizeIsIncorrect() = runTest(dispatcher) {
        val server = Server(id = 7L, name = "prod", host = "example.com", username = "demo")
        val project = Project(id = 11L, serverId = server.id, name = "music")
        val entry = RemoteFileEntry("growing.png", 1)
        whenever(serverRepo.getById(server.id)).thenReturn(server)
        whenever(projectRepo.getById(project.id)).thenReturn(project)
        whenever(downloader.listFiles(eq(server), isNull(), isNull(), any())).thenReturn(listOf(entry))
        whenever(downloader.download(eq(server), isNull(), isNull(), any(), eq(entry.name), any()))
            .thenAnswer { invocation ->
                val output = invocation.getArgument<OutputStream>(5)
                flow<ScpDownloadProgress> {
                    output.write(ByteArray(MAX_REMOTE_IMAGE_PREVIEW_BYTES.toInt() + 1))
                }
            }
        val viewModel = createViewModel()
        viewModel.loadRemoteFiles(server.id, project.id)
        advanceUntilIdle()
        viewModel.previewRemoteFile(server.id, project.id, entry)
        advanceUntilIdle()
        val state = viewModel.downloadState(project.id).first() as DownloadState.Error
        assertTrue(state.message.contains("8192 KB"))
    }

    @Test fun oversizedImageIsRejectedBeforeTransfer() = runTest(dispatcher) {
        val server = Server(id = 7L, name = "prod", host = "example.com", username = "demo")
        val project = Project(id = 11L, serverId = server.id, name = "music")
        val entry = RemoteFileEntry("huge.png", MAX_REMOTE_IMAGE_PREVIEW_BYTES + 1)
        whenever(serverRepo.getById(server.id)).thenReturn(server)
        whenever(projectRepo.getById(project.id)).thenReturn(project)
        whenever(downloader.listFiles(eq(server), isNull(), isNull(), any())).thenReturn(listOf(entry))
        val viewModel = createViewModel()
        viewModel.loadRemoteFiles(server.id, project.id)
        advanceUntilIdle()
        viewModel.previewRemoteFile(server.id, project.id, entry)
        assertTrue(viewModel.downloadState(project.id).first() is DownloadState.Error)
        verify(downloader, never()).download(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun batchDownloadWritesEverySelectedFileAndReportsCombinedResult() = runTest(dispatcher) {
        val server = Server(id = 7L, name = "prod", host = "example.com", username = "demo")
        val project = Project(id = 11L, serverId = server.id, name = "music")
        val context: Context = mock()
        val resolver: ContentResolver = mock()
        val firstUri: Uri = mock()
        val secondUri: Uri = mock()
        val firstOutput = ByteArrayOutputStream()
        val secondOutput = ByteArrayOutputStream()
        whenever(serverRepo.getById(server.id)).thenReturn(server)
        whenever(projectRepo.getById(project.id)).thenReturn(project)
        whenever(context.contentResolver).thenReturn(resolver)
        whenever(context.cacheDir).thenReturn(temporaryFolder.root)
        whenever(resolver.openOutputStream(firstUri)).thenReturn(firstOutput)
        whenever(resolver.openOutputStream(secondUri)).thenReturn(secondOutput)
        whenever(
            downloader.download(
                server = eq(server),
                password = isNull(),
                privateKeyPem = isNull(),
                remoteDir = eq("~/terminalhub/music/stems"),
                fileName = any(),
                outputStream = any()
            )
        ).thenAnswer { invocation ->
            val fileName = invocation.getArgument<String>(4)
            val output = invocation.getArgument<OutputStream>(5)
            flow {
                val content = "content-$fileName".toByteArray()
                output.write(content)
                emit(ScpDownloadProgress(fileName, content.size.toLong(), content.size.toLong()))
            }
        }
        val viewModel = createViewModel()

        viewModel.startDownloads(
            serverId = server.id,
            projectId = project.id,
            relativeDirectory = "stems",
            fileNames = listOf("kick.wav", "snare.wav"),
            context = context,
            createDestination = { fileName ->
                if (fileName == "kick.wav") firstUri else secondUri
            }
        )
        var finalState: DownloadState = DownloadState.Idle
        for (attempt in 0 until 100) {
            advanceUntilIdle()
            finalState = viewModel.downloadState(project.id).first()
            if (finalState is DownloadState.BatchDone || finalState is DownloadState.Error) {
                break
            }
            Thread.sleep(10)
        }

        assertEquals("content-kick.wav", firstOutput.toString())
        assertEquals("content-snare.wav", secondOutput.toString())
        assertEquals(
            DownloadState.BatchDone(
                listOf(
                    DownloadedRemoteFile("kick.wav", 16L, firstUri),
                    DownloadedRemoteFile("snare.wav", 17L, secondUri)
                )
            ),
            finalState
        )
        assertTrue(temporaryFolder.root.resolve("downloads").listFiles().orEmpty().isEmpty())
    }

    @Test fun repeatedDownloadTapsReserveOnlyOneBatchBeforeRepositoryReturns() = runTest(dispatcher) {
        val viewModel = createViewModel()
        val context: Context = mock()
        repeat(2) {
            viewModel.startDownloads(7, 11, "images", listOf("photo.png"), context) { mock() }
        }
        assertTrue(viewModel.downloadState(11).first() is DownloadState.Downloading)
        advanceUntilIdle()
        verify(serverRepo).getById(7)
    }

    @Test fun reopensRememberedDirectoryAfterCloseAndViewModelRecreation() = runTest(dispatcher) {
        val server = Server(id = 7L, name = "prod", host = "example.com", username = "demo")
        val project = Project(id = 11L, serverId = 7L, name = "music")
        val entries = listOf(RemoteFileEntry("photo.png", 10))
        whenever(serverRepo.getById(7)).thenReturn(server)
        whenever(projectRepo.getById(11)).thenReturn(project)
        whenever(browseLocationStore.lastDirectory(server, project, "~/terminalhub/music")).thenReturn("images/nested")
        whenever(downloader.listFiles(eq(server), isNull(), isNull(), eq("~/terminalhub/music/images/nested"))).thenReturn(entries)
        val first = createViewModel()
        first.loadRemoteFiles(7, 11)
        advanceUntilIdle()
        assertEquals(DownloadState.Listed("images/nested", entries), first.downloadState(11).first())
        first.reset(11)
        assertEquals(DownloadState.Idle, first.downloadState(11).first())
        val recreated = createViewModel()
        recreated.loadRemoteFiles(7, 11)
        advanceUntilIdle()
        assertEquals(DownloadState.Listed("images/nested", entries), recreated.downloadState(11).first())
    }

    @Test fun explicitRootNavigationOverridesAndReplacesRememberedSubfolder() = runTest(dispatcher) {
        val server = Server(id = 7L, name = "prod", host = "example.com", username = "demo")
        val project = Project(id = 11L, serverId = 7L, name = "music")
        whenever(serverRepo.getById(7)).thenReturn(server)
        whenever(projectRepo.getById(11)).thenReturn(project)
        whenever(downloader.listFiles(eq(server), isNull(), isNull(), eq("~/terminalhub/music"))).thenReturn(emptyList())
        val viewModel = createViewModel()
        viewModel.loadRemoteFiles(7, 11, "")
        advanceUntilIdle()
        assertEquals(DownloadState.Listed("", emptyList()), viewModel.downloadState(11).first())
        verify(browseLocationStore, never()).lastDirectory(any(), any(), any())
        verify(browseLocationStore).save(server, project, "~/terminalhub/music", "")
    }

    @Test fun unavailableFolderShowsRecoverablePathWithoutOverwritingLastGoodLocation() = runTest(dispatcher) {
        val server = Server(id = 7L, name = "prod", host = "example.com", username = "demo")
        val project = Project(id = 11L, serverId = 7L, name = "music")
        whenever(serverRepo.getById(7)).thenReturn(server)
        whenever(projectRepo.getById(11)).thenReturn(project)
        whenever(browseLocationStore.lastDirectory(server, project, "~/terminalhub/music")).thenReturn("removed")
        whenever(downloader.listFiles(eq(server), isNull(), isNull(), any())).thenAnswer { throw IOException("No such folder") }
        val viewModel = createViewModel()
        viewModel.loadRemoteFiles(7, 11)
        advanceUntilIdle()
        assertEquals("removed", (viewModel.downloadState(11).first() as DownloadState.Error).directory)
        verify(browseLocationStore, never()).save(any(), any(), any(), any())
    }

    @Test fun closingWhileListingCancelsRequestAndDoesNotSaveUnopenedDirectory() = runTest(dispatcher) {
        val server = Server(id = 7L, name = "prod", host = "example.com", username = "demo")
        val project = Project(id = 11L, serverId = 7L, name = "music")
        whenever(serverRepo.getById(7)).thenReturn(server)
        whenever(projectRepo.getById(11)).thenReturn(project)
        val gate = kotlinx.coroutines.CompletableDeferred<List<RemoteFileEntry>>()
        whenever(downloader.listFiles(eq(server), isNull(), isNull(), any())).doSuspendableAnswer { gate.await() }
        val viewModel = createViewModel()
        viewModel.loadRemoteFiles(7, 11, "new-folder")
        runCurrent()
        viewModel.reset(11)
        gate.complete(emptyList())
        advanceUntilIdle()
        assertEquals(DownloadState.Idle, viewModel.downloadState(11).first())
        verify(browseLocationStore, never()).save(any(), any(), any(), any())
    }

    private fun createViewModel() = FileDownloadViewModel(
        serverRepo = serverRepo,
        projectRepo = projectRepo,
        engine = engine,
        scpDownloader = downloader,
        securePrefs = securePrefs,
        settingsRepository = settingsRepository,
        browseLocationStore = browseLocationStore
    )
}
