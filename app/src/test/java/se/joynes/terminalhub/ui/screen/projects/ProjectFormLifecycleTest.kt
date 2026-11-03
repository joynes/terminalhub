package se.joynes.terminalhub.ui.screen.projects

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import se.joynes.terminalhub.data.model.*
import se.joynes.terminalhub.data.repository.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ProjectFormLifecycleTest {
    private val dispatcher = StandardTestDispatcher()
    private val projects: ProjectRepository = mock()
    private val servers: ServerRepository = mock()
    private lateinit var vm: AddEditProjectViewModel

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        whenever(servers.getAll()).thenReturn(flowOf(listOf(
            Server(id = 4, name = "First", host = "one", username = "user"),
            Server(id = 9, name = "Second", host = "two", username = "user"))))
        vm = AddEditProjectViewModel(projects, servers)
    }
    @After fun cleanup() { Dispatchers.resetMain() }

    @Test fun newSshProjectUsesRequestedServerAndNormalizesTheFolderName() = runTest(dispatcher) {
        vm.update { copy(targetType = ProjectTargetType.SSH) }
        vm.loadProject(9, null); runCurrent()
        assertEquals(9L, vm.state.value.selectedServerId)
        assertEquals(listOf("First", "Second"), vm.state.value.serverOptions.map { it.name })
        vm.updateName("demo site")
        vm.updateDisplayName("  Demo site  ")
        vm.update { copy(gitUrl = " https://github.com/example/demo ", aiCommand = "ai-client", customScript = "pwd", useTmux = true) }
        vm.save(); runCurrent()
        verify(projects).save(argThat {
            serverId == 9L && name == "demo-site" && displayName == "Demo site" &&
                gitUrl == "git@github.com:example/demo.git" && aiCommand == "ai-client" && customScript == "pwd" && useTmux
        })
        assertTrue(vm.state.value.saved)
    }

    @Test fun newSshProjectFallsBackToFirstServerButNeverOverwritesAnExplicitChoice() = runTest(dispatcher) {
        vm.update { copy(targetType = ProjectTargetType.SSH) }
        vm.loadProject(null, null); runCurrent()
        assertEquals(4L, vm.state.value.selectedServerId)
        vm.update { copy(selectedServerId = 9) }
        vm.loadProject(4, null); runCurrent()
        assertEquals(9L, vm.state.value.selectedServerId)
    }

    @Test fun localProjectSavesWithoutServerAndKeepsHttpsCloneUrl() = runTest(dispatcher) {
        vm.update { copy(targetType = ProjectTargetType.LOCAL, selectedServerId = 9, gitUrl = " https://github.com/example/demo ") }
        vm.loadProject(null, null); runCurrent()
        assertNull(vm.state.value.selectedServerId)
        vm.updateName("local"); vm.save(); runCurrent()
        verify(projects).save(argThat { serverId == LOCAL_PROJECT_SERVER_ID && isLocal && gitUrl == "https://github.com/example/demo" })
    }

    @Test fun editingDisplayTitlePreservesRemoteFolderAndOpenTimestamp() = runTest(dispatcher) {
        whenever(projects.getById(6)).thenReturn(Project(id = 6, serverId = 4, name = "remote-folder", lastOpenedAt = 123,
            displayName = "Old title", targetType = ProjectTargetType.SSH, colorSeed = 45))
        vm.loadProject(null, 6); runCurrent()
        vm.updateName("must-not-rename-remote")
        assertEquals("remote-folder", vm.state.value.name)
        vm.updateDisplayName("New title")
        vm.update { copy(name = "also-not-remote") }
        vm.save(); runCurrent()
        verify(projects).update(argThat { id == 6L && name == "remote-folder" && displayName == "New title" && lastOpenedAt == 123L && colorSeed == 45 })
        verify(projects, never()).save(any())
    }

    @Test fun invalidFolderOrDisplayTitleCannotBeSaved() = runTest(dispatcher) {
        vm.updateName("../escape"); vm.save(); runCurrent()
        assertNotNull(vm.state.value.nameError)
        vm.updateName("valid"); vm.updateDisplayName("bad\u0000title"); vm.save(); runCurrent()
        assertNotNull(vm.state.value.displayNameError)
        assertFalse(vm.state.value.saved)
        verify(projects, never()).save(any())
    }

    @Test fun sshProjectWithoutServerCannotSave() = runTest(dispatcher) {
        vm.update { copy(targetType = ProjectTargetType.SSH, name = "valid", selectedServerId = null) }
        vm.save(); runCurrent()
        assertFalse(vm.state.value.saved)
        verify(projects, never()).save(any())
    }

    @Test fun missingEditedProjectPreservesTheDraftAndEmptyServerListIsSupported() = runTest(dispatcher) {
        whenever(servers.getAll()).thenReturn(flowOf(emptyList()))
        vm.updateName("draft"); vm.loadProject(null, 99); runCurrent()
        assertEquals("draft", vm.state.value.name)
        assertTrue(vm.state.value.serverOptions.isEmpty())
        assertNull(vm.state.value.selectedServerId)
    }
}
