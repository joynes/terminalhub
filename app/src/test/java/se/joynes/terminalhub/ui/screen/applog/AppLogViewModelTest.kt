package se.joynes.terminalhub.ui.screen.applog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import se.joynes.terminalhub.data.model.AppLogEntry
import se.joynes.terminalhub.data.repository.AppLogRepository
import se.joynes.terminalhub.domain.usecase.ExportSessionLog

@OptIn(ExperimentalCoroutinesApi::class)
class AppLogViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository: AppLogRepository = mock()
    private val exporter: ExportSessionLog = mock()
    private val recent = MutableStateFlow(listOf(entry(1, "recent")))
    private val errors = MutableStateFlow(listOf(entry(2, "error")))
    private val matches = MutableStateFlow(listOf(entry(3, "matching")))
    private lateinit var vm: AppLogViewModel
    private fun entry(id: Long, text: String) = AppLogEntry(id, 1, "INFO", "fixture", text)
    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        whenever(repository.recentLogs()).thenReturn(recent)
        whenever(repository.logsByLevel("ERROR")).thenReturn(errors)
        whenever(repository.search(any(), any())).thenReturn(matches)
        vm = AppLogViewModel(repository, exporter)
    }
    @After fun cleanup() { Dispatchers.resetMain() }
    @Test fun subscribedLogsDebounceSearchAndSwitchBackToSelectedLevelWhenCleared() = runTest(dispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.logs.collect {} }
        advanceTimeBy(301); runCurrent()
        assertEquals(recent.value, vm.logs.value)
        vm.setLevel("ERROR"); runCurrent()
        assertEquals(errors.value, vm.logs.value)
        vm.setSearch("first"); advanceTimeBy(200)
        vm.setSearch("final"); advanceTimeBy(299); runCurrent()
        verify(repository, never()).search(any(), any())
        advanceTimeBy(2); runCurrent()
        verify(repository).search("final")
        assertEquals(matches.value, vm.logs.value)
        vm.setSearch("  "); advanceTimeBy(301); runCurrent()
        assertEquals(errors.value, vm.logs.value)
        vm.setLevel("ALL"); runCurrent()
        assertEquals(recent.value, vm.logs.value)
    }
    @Test fun repositoryUpdatesRemainLiveAndAutoscrollCanBeToggledIndependently() = runTest(dispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.logs.collect {} }
        advanceTimeBy(301); runCurrent()
        recent.value = listOf(entry(4, "new")); runCurrent()
        assertEquals("new", vm.logs.value.single().message)
        assertTrue(vm.autoScroll.value)
        vm.toggleAutoScroll(); assertFalse(vm.autoScroll.value)
        vm.toggleAutoScroll(); assertTrue(vm.autoScroll.value)
        assertEquals("", vm.searchQuery.value); assertEquals("ALL", vm.selectedLevel.value)
    }
}
