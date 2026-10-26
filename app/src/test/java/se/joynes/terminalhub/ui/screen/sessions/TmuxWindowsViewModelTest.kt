package se.joynes.terminalhub.ui.screen.sessions

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import se.joynes.terminalhub.data.model.*
import se.joynes.terminalhub.data.repository.*
import se.joynes.terminalhub.data.ssh.*

@OptIn(ExperimentalCoroutinesApi::class)
class TmuxWindowsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val projects: ProjectRepository = mock()
    private val servers: ServerRepository = mock()
    private val remote: RemoteTmuxWindows = mock()
    private val server = Server(id = 1, name = "Server", host = "fixture", username = "fixture")
    private val a = Project(id = 1, serverId = 1, name = "one")
    private val b = Project(id = 2, serverId = 1, name = "two")
    private val first = TmuxWindow("@1", 0, "Codex", true, 1)
    private val second = TmuxWindow("@2", 1, "Tests", false, 1)
    private val snapshot = TmuxWindowsSnapshot("\$1", listOf(first, second))
    private lateinit var vm: TmuxWindowsViewModel
    @Before fun setup() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        whenever(projects.getById(1)).thenReturn(a)
        whenever(projects.getById(2)).thenReturn(b)
        whenever(servers.getById(1)).thenReturn(server)
        whenever(remote.list(server, a)).thenReturn(snapshot)
        whenever(remote.list(server, b)).thenReturn(TmuxWindowsSnapshot("\$2", listOf(first.copy(id = "@9"))))
        vm = TmuxWindowsViewModel(projects, servers, remote)
    }
    @After fun cleanup() { vm.pause(); Dispatchers.resetMain() }

    @Test fun `binding only lists and never creates or switches windows`() = runTest(dispatcher) {
        vm.bind(1, "connection-1", true); runCurrent()
        assertEquals(snapshot, vm.state.value.snapshot)
        verify(remote, never()).create(any(), any(), any(), any())
        verify(remote, never()).select(any(), any(), any())
        val revision = vm.state.value.selectionRevision
        vm.pause(); vm.bind(1, "connection-2", true); runCurrent()
        assertEquals(snapshot, vm.state.value.snapshot)
        assertEquals(revision, vm.state.value.selectionRevision)
        vm.pause()
    }

    @Test fun `double tapping create is one operation with immediate progress`() = runTest(dispatcher) {
        vm.bind(1, "connection", true); runCurrent()
        whenever(remote.create(server, a, snapshot, "Logs")).thenReturn(snapshot)
        vm.create("Logs"); vm.create("Logs")
        assertTrue(vm.state.value.busy)
        assertEquals("Opening terminal…", vm.state.value.operation)
        runCurrent()
        verify(remote, times(1)).create(server, a, snapshot, "Logs")
        assertFalse(vm.state.value.busy)
        vm.pause()
    }

    @Test fun `switch uses stable window id and external changes update active terminal`() = runTest(dispatcher) {
        vm.bind(1, "connection", true); runCurrent()
        val switched = snapshot.copy(windows = listOf(first.copy(active = false), second.copy(active = true)))
        whenever(remote.select(server, snapshot, second)).thenReturn(switched)
        whenever(remote.list(server, a)).thenReturn(switched)
        vm.select(second); runCurrent()
        assertEquals("@2", vm.state.value.snapshot!!.windows.single { it.active }.id)
        assertEquals(1L, vm.state.value.selectionRevision)
        advanceTimeBy(3000); runCurrent()
        assertEquals(1L, vm.state.value.selectionRevision)
        vm.pause()
    }

    @Test fun `failed mutation keeps existing windows and refresh does not retry creation`() = runTest(dispatcher) {
        vm.bind(1, "connection", true); runCurrent()
        whenever(remote.create(server, a, snapshot, "Logs")).thenThrow(IllegalStateException("Refresh before trying again"))
        vm.create("Logs"); runCurrent()
        assertEquals(snapshot, vm.state.value.snapshot)
        assertFalse(vm.state.value.busy)
        assertNotNull(vm.state.value.error)
        vm.refresh(); runCurrent()
        verify(remote, times(1)).create(server, a, snapshot, "Logs")
        assertNull(vm.state.value.error)
        vm.pause()
    }

    @Test fun `project switching does not select windows and pause stops background polling`() = runTest(dispatcher) {
        vm.bind(1, "one", true); runCurrent()
        vm.bind(2, "two", true); runCurrent()
        assertEquals(2L, vm.state.value.projectId)
        assertEquals("\$2", vm.state.value.snapshot!!.sessionId)
        vm.bind(1, "one", true); runCurrent()
        assertEquals(snapshot, vm.state.value.snapshot)
        assertEquals(0L, vm.state.value.selectionRevision)
        vm.pause(); clearInvocations(remote)
        advanceTimeBy(10_000); runCurrent()
        verifyNoInteractions(remote)
    }
}
