package se.joynes.terminalhub.data.ssh

import com.trilead.ssh2.Connection
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*
import se.joynes.terminalhub.data.logging.*
import se.joynes.terminalhub.data.runtime.*
import se.joynes.terminalhub.data.settings.*

@OptIn(ExperimentalCoroutinesApi::class)
class SshKeepaliveTransportTest {
    private val connection: Connection = mock()
    private val logger: AppLogger = mock()
    private val repository: AppSettingsRepository = mock()
    private val runtime: AppRuntimeRepository = mock()
    private val settings = MutableStateFlow(AppSettings())
    private val state = MutableStateFlow(AppRuntimeState(appInForeground = true))
    private fun transport(scope: kotlinx.coroutines.CoroutineScope): TrileadSshTransport {
        whenever(repository.settings).thenReturn(settings)
        whenever(runtime.state).thenReturn(state)
        return TrileadSshTransport(connection, logger, repository, runtime, scope)
    }
    @Test fun foregroundKeepaliveRunsEveryMinuteAndCloseCancelsFuturePackets() = runTest {
        val transport = transport(backgroundScope)
        runCurrent(); advanceTimeBy(59_999); runCurrent()
        verify(connection, never()).sendIgnorePacket()
        advanceTimeBy(1); runCurrent()
        verify(connection).sendIgnorePacket()
        transport.close()
        advanceTimeBy(180_000); runCurrent()
        verify(connection, times(1)).sendIgnorePacket()
        verify(connection).close()
    }
    @Test fun everyBackgroundProfileUsesItsOwnIntervalAndRequiresAnEligibleProject() = runTest {
        state.value = state.value.copy(appInForeground = false, activeProjectId = 7)
        val intervals = listOf(BackgroundKeepaliveProfile.AGGRESSIVE to 30_000L,
            BackgroundKeepaliveProfile.BALANCED to 120_000L,
            BackgroundKeepaliveProfile.BATTERY_SAVER to 300_000L,
            BackgroundKeepaliveProfile.ULTRA_BATTERY_SAVER to 600_000L)
        for ((profile, interval) in intervals) {
            clearInvocations(connection)
            settings.value = settings.value.copy(backgroundKeepaliveProfile = profile, backgroundKeepaliveScope = BackgroundKeepaliveScope.ALL_SESSIONS)
            val transport = transport(backgroundScope)
            runCurrent(); advanceTimeBy(interval); runCurrent()
            verify(connection, never()).sendIgnorePacket()
            transport.updateProjectIds(setOf(7))
            advanceTimeBy(interval); runCurrent()
            verify(connection).sendIgnorePacket()
            transport.close()
        }
    }
    @Test fun activeTabScopeSkipsOtherProjectsAndDisabledKeepaliveSkipsAllProjects() = runTest {
        settings.value = settings.value.copy(backgroundKeepaliveProfile = BackgroundKeepaliveProfile.AGGRESSIVE,
            backgroundKeepaliveScope = BackgroundKeepaliveScope.ACTIVE_TAB_ONLY)
        state.value = state.value.copy(appInForeground = false, activeProjectId = 9)
        val transport = transport(backgroundScope)
        transport.updateProjectIds(setOf(7)); runCurrent()
        advanceTimeBy(30_000); runCurrent()
        verify(connection, never()).sendIgnorePacket()
        state.value = state.value.copy(activeProjectId = 7)
        advanceTimeBy(30_000); runCurrent()
        verify(connection).sendIgnorePacket()
        settings.value = settings.value.copy(sshKeepaliveEnabled = false)
        advanceTimeBy(60_000); runCurrent()
        verify(connection, times(1)).sendIgnorePacket()
        transport.close()
    }
    @Test fun aFailedPacketIsLoggedAndTheNextIntervalStillRuns() = runTest {
        doAnswer { throw IOException("transport closed") }.whenever(connection).sendIgnorePacket()
        val transport = transport(backgroundScope)
        runCurrent(); advanceTimeBy(120_000); runCurrent()
        verify(connection, times(2)).sendIgnorePacket()
        verify(logger, times(2)).log(eq(LogLevel.WARN), eq("SharedSshTransport"), argThat { contains("transport closed") }, isNull())
        transport.close()
    }
}
