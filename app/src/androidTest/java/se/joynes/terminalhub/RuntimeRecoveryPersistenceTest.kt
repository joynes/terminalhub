package se.joynes.terminalhub

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import java.util.UUID
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import se.joynes.terminalhub.data.runtime.AppRuntimeRepository

class RuntimeRecoveryPersistenceTest {
    private val base = ApplicationProvider.getApplicationContext<Context>()
    private val preferenceName = "runtime-test-${UUID.randomUUID()}"
    private lateinit var context: Context
    @Before fun setup() {
        context = object : ContextWrapper(base) {
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = base.getSharedPreferences(preferenceName, mode)
        }
    }
    @After fun cleanup() { base.deleteSharedPreferences(preferenceName) }
    @Test fun firstProcessHasNoRecoveryAndForegroundTimestampsSurviveRecreation() {
        val repository = AppRuntimeRepository(context)
        assertNull(repository.state.value.lastForegroundAt)
        assertNull(repository.state.value.lastBackgroundAt)
        val started = repository.onProcessStarted()
        assertTrue(started.processToken.isNotBlank()); assertTrue(started.processStartedAt > 0)
        assertFalse(started.recoveryPending); assertNull(started.lastProcessRestartReason)
        repository.noteAppForeground()
        assertTrue(repository.state.value.appInForeground)
        repository.noteAppBackground()
        val restored = AppRuntimeRepository(context).state.value
        assertFalse(restored.appInForeground)
        assertNotNull(restored.lastForegroundAt); assertNotNull(restored.lastBackgroundAt)
        assertEquals(started.processToken, restored.processToken)
    }
    @Test fun processLossRemembersBothKindsOfProjectAndTheSelectedProject() {
        val first = AppRuntimeRepository(context)
        first.onProcessStarted(); first.noteSessions(setOf(4, 7), setOf(9), 7)
        first.noteForegroundServiceStarted(); first.noteAppBackground()
        val recovered = AppRuntimeRepository(context).onProcessStarted()
        assertTrue(recovered.recoveryPending)
        assertEquals(setOf(4L, 7L), recovered.recoveryRemoteProjectIds)
        assertEquals(setOf(9L), recovered.recoveryLocalProjectIds)
        assertEquals(7L, recovered.recoveryActiveProjectId)
        assertTrue(recovered.remoteProjectIds.isEmpty()); assertTrue(recovered.localProjectIds.isEmpty())
        assertFalse(recovered.foregroundServiceRunning)
        assertTrue(recovered.lastProcessRestartReason!!.contains("remoteProjects=[4, 7]"))
        assertNotEquals(first.state.value.processToken, recovered.processToken)
    }
    @Test fun repeatedProcessLossBeforeReconnectKeepsTheOriginalRecoveryTargets() {
        val first = AppRuntimeRepository(context)
        first.noteSessions(setOf(4), setOf(9), 9)
        AppRuntimeRepository(context).onProcessStarted()
        val afterAnotherRestart = AppRuntimeRepository(context).onProcessStarted()
        assertTrue(afterAnotherRestart.recoveryPending)
        assertEquals(setOf(4L), afterAnotherRestart.recoveryRemoteProjectIds)
        assertEquals(setOf(9L), afterAnotherRestart.recoveryLocalProjectIds)
        assertEquals(9L, afterAnotherRestart.recoveryActiveProjectId)
    }
    @Test fun reconnectClearsPendingFlagAndExplicitResetClearsPersistedSessionAndDisconnectState() {
        val repository = AppRuntimeRepository(context)
        repository.noteSessions(setOf(1), emptySet(), 1)
        repository.onProcessStarted()
        repository.noteSessions(emptySet(), emptySet(), null)
        assertTrue(repository.state.value.recoveryPending)
        repository.noteSessions(emptySet(), setOf(8), 8)
        assertFalse(repository.state.value.recoveryPending)
        repository.noteSshDisconnect(1, "network lost")
        assertEquals("network lost", AppRuntimeRepository(context).state.value.lastSshDisconnectSummary)
        assertNotNull(repository.state.value.lastSshDisconnectAt)
        repository.clearSessionState()
        val reset = AppRuntimeRepository(context).state.value
        assertTrue(reset.remoteProjectIds.isEmpty()); assertTrue(reset.localProjectIds.isEmpty())
        assertTrue(reset.recoveryRemoteProjectIds.isEmpty()); assertTrue(reset.recoveryLocalProjectIds.isEmpty())
        assertNull(reset.activeProjectId); assertNull(reset.recoveryActiveProjectId)
        assertNull(reset.lastSshDisconnectAt); assertNull(reset.lastSshDisconnectProjectId); assertNull(reset.lastSshDisconnectSummary)
    }
    @Test fun serviceStopRetainsReasonAndSnapshotWithoutPretendingItIsStillRunning() {
        val repository = AppRuntimeRepository(context)
        repository.noteForegroundServiceRunning(true)
        assertTrue(repository.state.value.foregroundServiceRunning)
        repository.noteForegroundServiceStarted()
        repository.noteForegroundServiceStopped("user_settings", "fixture snapshot")
        val restored = AppRuntimeRepository(context).state.value
        assertFalse(restored.foregroundServiceRunning)
        assertNotNull(restored.lastServiceStartedAt); assertNotNull(restored.lastServiceStopAt)
        assertEquals("user_settings", restored.lastServiceStopReason); assertEquals("fixture snapshot", restored.lastServiceStopSnapshot)
    }
    @Test fun invalidPersistedProjectIdsAreIgnoredWhileValidIdsRemainAvailable() {
        context.getSharedPreferences("app_runtime", 0).edit()
            .putStringSet("remote_project_ids", setOf("4", "invalid", "9999999999999999999999"))
            .putStringSet("local_project_ids", setOf("8", ""))
            .putStringSet("recovery_remote_project_ids", setOf("12", "wrong"))
            .putStringSet("recovery_local_project_ids", setOf("15", "wrong"))
            .commit()
        val state = AppRuntimeRepository(context).state.value
        assertEquals(setOf(4L), state.remoteProjectIds); assertEquals(setOf(8L), state.localProjectIds)
        assertEquals(setOf(12L), state.recoveryRemoteProjectIds); assertEquals(setOf(15L), state.recoveryLocalProjectIds)
    }
}
