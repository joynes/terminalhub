package se.joynes.terminalhub.ui.screen.git

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import se.joynes.terminalhub.data.git.*
import se.joynes.terminalhub.data.model.*
import se.joynes.terminalhub.data.repository.*
import se.joynes.terminalhub.domain.ScriptTemplateEngine

@OptIn(ExperimentalCoroutinesApi::class)
class GitReviewViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val projects: ProjectRepository = mock()
    private val servers: ServerRepository = mock()
    private val executor: RemoteGitExecutor = mock()
    private val server = Server(id = 1, name = "test", host = "fixture", username = "user", projectsFolder = "/projects")
    private val project = Project(id = 2, serverId = 1, name = "demo", displayName = "Demo title")
    private val root = "/projects/demo"
    private val changed = GitChangedFile("tracked.txt", "M")
    private lateinit var vm: GitReviewViewModel

    @Before fun setup() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        whenever(projects.getById(2)).thenReturn(project)
        whenever(servers.getById(1)).thenReturn(server)
        whenever(executor.run(eq(server), any())).thenAnswer {
            val command = it.getArgument<String>(1)
            when {
                command == GitReviewCommands.containingRepository(root) -> GitCommandResult("$root\n", 0)
                command == GitReviewCommands.nestedRepositories(root) -> GitCommandResult("", 0)
                command == GitReviewCommands.hasCommit(root) -> GitCommandResult("abc", 0)
                command == GitReviewCommands.branch(root) -> GitCommandResult("main\n", 0)
                command == GitReviewCommands.latestCommit(root) -> GitCommandResult("abc · message\n", 0)
                command == GitReviewCommands.untracked(root) -> GitCommandResult("new.txt\u0000", 0)
                GitReviewMode.entries.any { mode -> command == GitReviewCommands.files(root, mode, true) } -> GitCommandResult("M\u0000tracked.txt\u0000", 0)
                GitReviewMode.entries.any { mode -> command == GitReviewCommands.diff(root, mode, true, changed) } -> GitCommandResult("@@ -1 +1 @@\n-old\n+new\n", 0, true)
                else -> error("Unexpected Git read")
            }
        }
        vm = GitReviewViewModel(projects, servers, ScriptTemplateEngine(), executor)
    }
    @After fun cleanup() { vm.close(); Dispatchers.resetMain() }

    @Test fun singleRepositoryOpensItsSnapshotAndSelectingFileLoadsBoundedDiff() = runTest(dispatcher) {
        vm.open(2); runCurrent()
        assertEquals("Demo title", vm.state.value.projectName)
        assertEquals(root, vm.state.value.repository)
        assertEquals("main", vm.state.value.snapshot!!.branch)
        assertEquals(listOf(changed, GitChangedFile("new.txt", "?")), vm.state.value.snapshot!!.files)
        vm.selectFile(changed); runCurrent()
        assertEquals(changed, vm.state.value.file)
        assertTrue(vm.state.value.patch!!.contains("+new"))
        assertTrue(vm.state.value.truncated)
        vm.backToFiles()
        assertNull(vm.state.value.file)
        assertNull(vm.state.value.patch)
        assertFalse(vm.state.value.loading)
    }

    @Test fun modeChangesClearOldDiffAndRefreshKeepsSelectedMode() = runTest(dispatcher) {
        vm.open(2); runCurrent(); vm.selectFile(changed); runCurrent()
        vm.selectMode(GitReviewMode.STAGED); runCurrent()
        assertEquals(GitReviewMode.STAGED, vm.state.value.mode)
        assertNull(vm.state.value.file)
        assertNull(vm.state.value.patch)
        assertFalse(vm.state.value.truncated)
        assertEquals(listOf(changed), vm.state.value.snapshot!!.files)
        vm.refresh(); runCurrent()
        assertEquals(GitReviewMode.STAGED, vm.state.value.mode)
        assertFalse(vm.state.value.loading)
    }

    @Test fun forgedRepositoryAndFileCannotTriggerRemoteReads() = runTest(dispatcher) {
        vm.selectFile(changed); vm.selectMode(GitReviewMode.COMMIT); vm.refresh(); runCurrent()
        verifyNoInteractions(executor)
        vm.open(2); runCurrent(); clearInvocations(executor)
        vm.selectRepository("/not-this-project")
        vm.selectFile(GitChangedFile("secret.txt", "M")); runCurrent()
        verifyNoInteractions(executor)
        assertNull(vm.state.value.file)
    }

    @Test fun deletedAndLocalProjectsFailBeforeAnyRemoteCommand() = runTest(dispatcher) {
        vm.open(99); runCurrent()
        assertEquals("Project no longer exists.", vm.state.value.error)
        whenever(projects.getById(2)).thenReturn(project.copy(targetType = ProjectTargetType.LOCAL))
        vm.open(2); runCurrent()
        assertTrue(vm.state.value.error!!.contains("requires an SSH project"))
        whenever(projects.getById(2)).thenReturn(project)
        whenever(servers.getById(1)).thenReturn(null)
        vm.open(2); runCurrent()
        assertEquals("Server no longer exists.", vm.state.value.error)
        verifyNoInteractions(executor)
    }

    @Test fun commandFailureIsShownAndRefreshRetriesDiscovery() = runTest(dispatcher) {
        whenever(executor.run(eq(server), any())).thenThrow(IllegalStateException("Network unavailable"))
        vm.open(2); runCurrent()
        assertEquals("Network unavailable", vm.state.value.error)
        assertFalse(vm.state.value.loading)
        vm.refresh(); runCurrent()
        verify(executor, times(2)).run(eq(server), any())
    }

    @Test fun cancelledRemoteReadIsNotReportedAsAUserVisibleFailure() = runTest(dispatcher) {
        whenever(executor.run(eq(server), any())).thenThrow(CancellationException("Viewer closed"))
        vm.open(2); runCurrent(); vm.close()
        assertNull(vm.state.value.error)
    }
}
