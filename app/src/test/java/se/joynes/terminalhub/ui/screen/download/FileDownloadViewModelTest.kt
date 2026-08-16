package se.joynes.terminalhub.ui.screen.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import se.joynes.terminalhub.data.model.Project
import se.joynes.terminalhub.data.model.ProjectTargetType
import se.joynes.terminalhub.data.model.Server
import se.joynes.terminalhub.data.repository.ProjectRepository
import se.joynes.terminalhub.data.repository.ServerRepository
import se.joynes.terminalhub.data.security.SecurePrefsManager
import se.joynes.terminalhub.data.ssh.RemoteFileEntry
import se.joynes.terminalhub.data.ssh.ScpDownloader
import se.joynes.terminalhub.domain.ScriptTemplateEngine

@OptIn(ExperimentalCoroutinesApi::class)
class FileDownloadViewModelTest {
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

        assertEquals(DownloadState.Listed(expected), viewModel.downloadState(project.id).first())
        verify(downloader).listFiles(
            server = eq(server),
            password = isNull(),
            privateKeyPem = isNull(),
            remoteDir = eq("~/projects with spaces/midi-musik")
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

    private fun createViewModel() = FileDownloadViewModel(
        serverRepo = serverRepo,
        projectRepo = projectRepo,
        engine = engine,
        scpDownloader = downloader,
        securePrefs = securePrefs
    )
}
