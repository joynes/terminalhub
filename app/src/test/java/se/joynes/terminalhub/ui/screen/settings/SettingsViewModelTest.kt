package se.joynes.terminalhub.ui.screen.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.*
import se.joynes.terminalhub.data.repository.InputActionsRepository
import se.joynes.terminalhub.data.runtime.*
import se.joynes.terminalhub.data.settings.*
import se.joynes.terminalhub.service.BackgroundSshService

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val context: Context = mock()
    private val repository: AppSettingsRepository = mock()
    private val runtime: AppRuntimeRepository = mock()
    private val inputs: InputActionsRepository = mock()
    private val files: SettingsFileManager = mock()
    private val uri: Uri = mock()
    private val state = MutableStateFlow(AppSettings())
    private val runtimeState = MutableStateFlow(AppRuntimeState())
    private val mode = BackgroundSshModeController()
    private lateinit var vm: SettingsViewModel
    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        whenever(repository.settings).thenReturn(state)
        whenever(runtime.state).thenReturn(runtimeState)
        vm = SettingsViewModel(context, repository, runtime, mode, inputs, files)
    }
    @After fun cleanup() { Dispatchers.resetMain() }
    @Test fun exportReportsSuccessAndClearsBusyFlagEvenOnFailure() = runTest(dispatcher) {
        vm.exportSettings(uri); runCurrent()
        assertFalse(vm.settingsFileBusy.value)
        assertEquals("Settings-only YAML exported", vm.settingsFileMessage.value)
        whenever(files.export(uri)).thenThrow(IllegalStateException("Read only"))
        vm.exportSettings(uri); runCurrent()
        assertFalse(vm.settingsFileBusy.value); assertEquals("Read only", vm.settingsFileMessage.value)
    }
    @Test fun previewLoadsWithoutMutatingSettingsAndReportsInvalidDocuments() = runTest(dispatcher) {
        val preview = SettingsImportPreview(state.value, state.value.copy(preferFastResume = false), emptyList())
        whenever(files.preview(uri)).thenReturn(preview)
        vm.previewSettings(uri); runCurrent()
        assertEquals(preview, vm.settingsPreview.value); assertFalse(vm.settingsFileBusy.value)
        verify(repository, never()).applySettingsImport(any(), any())
        whenever(files.preview(uri)).thenThrow(IllegalArgumentException("Invalid YAML"))
        vm.previewSettings(uri); runCurrent()
        assertFalse(vm.settingsFileBusy.value); assertEquals("Invalid YAML", vm.settingsFileMessage.value)
    }
    @Test fun anonymousFileFailuresUseOperationSpecificMessages() = runTest(dispatcher) {
        whenever(files.export(uri)).thenThrow(RuntimeException())
        whenever(files.preview(uri)).thenThrow(RuntimeException())
        vm.exportSettings(uri); runCurrent()
        assertEquals("Settings export failed", vm.settingsFileMessage.value)
        vm.previewSettings(uri); runCurrent()
        assertEquals("Settings import failed", vm.settingsFileMessage.value)
    }
    @Test fun sensitivePreviewRequiresApprovalAndStalePreviewNeverChangesPreferences() {
        val base = state.value
        val next = base.copy(inputHistoryEnabled = true)
        vm.settingsPreview.value = SettingsImportPreview(base, next, listOf(SettingsChange("inputHistoryEnabled", "false", "true")))
        vm.applySettingsPreview()
        verify(repository, never()).applySettingsImport(any(), any())
        assertTrue(vm.settingsFileMessage.value!!.contains("Explicit approval"))
        vm.applySettingsPreview(sensitiveChangesApproved = true)
        verify(repository).applySettingsImport(base, next)
        assertNull(vm.settingsPreview.value)
        assertTrue(vm.settingsFileMessage.value!!.contains("Imported 1 setting changes"))
        clearInvocations(repository)
        vm.settingsPreview.value = SettingsImportPreview(base, next, emptyList())
        state.value = base.copy(preferFastResume = false)
        vm.applySettingsPreview(true)
        verify(repository, never()).applySettingsImport(any(), any())
        assertTrue(vm.settingsFileMessage.value!!.contains("Settings changed during preview"))
    }
    @Test fun previewWithoutSensitiveChangesAppliesWithoutApprovalAndMissingPreviewIsANoOp() {
        vm.applySettingsPreview()
        verify(repository, never()).applySettingsImport(any(), any())
        val base = state.value; val next = base.copy(executeTextInputOnSend = false)
        vm.settingsPreview.value = SettingsImportPreview(base, next, emptyList())
        vm.applySettingsPreview()
        verify(repository).applySettingsImport(base, next)
        assertNull(vm.settingsPreview.value)
    }
    @Test fun permissionDenialNeverStartsServiceAndClearsRememberedBackgroundMode() {
        assertEquals(BackgroundSshStartResult.NOTIFICATION_PERMISSION_REQUIRED, vm.startBackgroundSsh(false))
        verify(repository).setKeepSshActiveInBackground(false)
        assertEquals(BackgroundSshMode.OFF, mode.mode.value)
        verifyNoInteractions(context)
        mode.dispatch(BackgroundSshEvent.ServiceStarted)
        vm.notificationPermissionDenied()
        assertEquals(BackgroundSshMode.OFF, mode.mode.value)
    }
    @Test fun explicitStartStartsTheServiceOnlyOnceAndRecordsApproval() {
        Mockito.mockConstruction(Intent::class.java, Mockito.withSettings().defaultAnswer(Mockito.RETURNS_SELF)).use { intents ->
            runtimeState.value = AppRuntimeState(remoteProjectIds = setOf(4, 7))
            assertEquals(BackgroundSshStartResult.STARTED, vm.startBackgroundSsh(true))
            assertEquals(BackgroundSshMode.STARTING, mode.mode.value)
            verify(repository).setKeepSshActiveInBackground(true)
            verify(intents.constructed().single()).setAction(BackgroundSshService.ACTION_START)
            assertEquals(BackgroundSshStartResult.STARTED, vm.startBackgroundSsh(true))
            assertEquals(1, intents.constructed().size)
        }
    }
    @Test fun failedServiceStartRollsBackPreferenceAndPolicy() {
        Mockito.mockConstruction(Intent::class.java, Mockito.withSettings().defaultAnswer(Mockito.RETURNS_SELF)).use {
            whenever(context.startService(any())).thenThrow(IllegalStateException("OS rejected service"))
            assertEquals(BackgroundSshStartResult.START_FAILED, vm.startBackgroundSsh(true))
            verify(repository).setKeepSshActiveInBackground(false)
            assertEquals(BackgroundSshMode.OFF, mode.mode.value)
        }
    }
    @Test fun stoppingALostServiceNeverStartsOrClosesRestoredTransports() {
        mode.dispatch(BackgroundSshEvent.ServiceStarted)
        vm.stopBackgroundSsh()
        verify(repository).setKeepSshActiveInBackground(false)
        assertEquals(BackgroundSshMode.OFF, mode.mode.value)
        verifyNoInteractions(context)
    }
    @Test fun stoppingARunningServiceRequestsExplicitTransportClosure() {
        Mockito.mockConstruction(Intent::class.java, Mockito.withSettings().defaultAnswer(Mockito.RETURNS_SELF)).use { intents ->
            runtimeState.value = AppRuntimeState(foregroundServiceRunning = true)
            mode.dispatch(BackgroundSshEvent.ServiceStarted)
            vm.stopBackgroundSsh()
            val intent = intents.constructed().single()
            verify(intent).setAction(BackgroundSshService.ACTION_STOP)
            verify(intent).putExtra("close_transports", true)
            verify(context).startService(intent)
            assertEquals(BackgroundSshMode.STOPPING, mode.mode.value)
        }
    }
    @Test fun automaticStartCanBeRememberedAfterApprovalAndDisabledWithoutStartingService() {
        state.value = state.value.copy(keepSshActiveInBackground = true)
        vm.setAutomaticallyStartBackgroundSsh(true)
        vm.setAutomaticallyStartBackgroundSsh(false)
        verify(repository).setAutomaticallyStartBackgroundSsh(true)
        verify(repository).setAutomaticallyStartBackgroundSsh(false)
        verifyNoInteractions(context)
    }
}
