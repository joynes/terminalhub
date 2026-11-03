package se.joynes.terminalhub.ui.screen.export

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import se.joynes.terminalhub.data.export.ExportImportManager
import se.joynes.terminalhub.data.export.ImportResult

@OptIn(ExperimentalCoroutinesApi::class)
class ExportImportViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val manager: ExportImportManager = mock()
    private val context: Context = mock()
    private val uri: Uri = mock()
    private lateinit var vm: ExportImportViewModel
    @Before fun setup() { Dispatchers.setMain(dispatcher); vm = ExportImportViewModel(manager) }
    @After fun cleanup() { Dispatchers.resetMain() }
    @Test fun exportForwardsSelectionAndLocalNoteConsentAndCanBeReset() = runTest(dispatcher) {
        assertEquals(ExportImportState.Idle, vm.state.value)
        vm.export(context, uri, setOf(2, 4), true)
        assertEquals(ExportImportState.Working, vm.state.value)
        runCurrent()
        verify(manager).exportYaml(context, uri, setOf(2, 4), true)
        assertEquals(ExportImportState.ExportDone, vm.state.value)
        vm.resetState(); assertEquals(ExportImportState.Idle, vm.state.value)
    }
    @Test fun importShowsActualCountsAndCanBeReset() = runTest(dispatcher) {
        whenever(manager.importYaml(context, uri)).thenReturn(ImportResult(2, 5))
        vm.import(context, uri)
        assertEquals(ExportImportState.Working, vm.state.value)
        runCurrent(); assertEquals(ExportImportState.ImportDone(ImportResult(2, 5)), vm.state.value)
        vm.resetState(); assertEquals(ExportImportState.Idle, vm.state.value)
    }
    @Test fun exportFailureCanBeRetriedWithoutStuckWorkingState() = runTest(dispatcher) {
        whenever(manager.exportYaml(context, uri, null, false)).thenThrow(IllegalStateException("Disk full")).thenAnswer { Unit }
        vm.export(context, uri); runCurrent()
        assertEquals(ExportImportState.Error("Disk full"), vm.state.value)
        vm.export(context, uri); runCurrent()
        assertEquals(ExportImportState.ExportDone, vm.state.value)
    }
    @Test fun failuresWithoutMessagesUseOperationSpecificFallbacks() = runTest(dispatcher) {
        whenever(manager.exportYaml(context, uri, null, false)).thenThrow(RuntimeException())
        vm.export(context, uri); runCurrent()
        assertEquals(ExportImportState.Error("Export failed"), vm.state.value)
        whenever(manager.importYaml(context, uri)).thenThrow(RuntimeException())
        vm.import(context, uri); runCurrent()
        assertEquals(ExportImportState.Error("Import failed"), vm.state.value)
    }
    @Test fun importFailureDisplaysReason() = runTest(dispatcher) {
        whenever(manager.importYaml(context, uri)).thenThrow(IllegalArgumentException("Invalid backup"))
        vm.import(context, uri); runCurrent()
        assertEquals(ExportImportState.Error("Invalid backup"), vm.state.value)
    }
}
