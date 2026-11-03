package se.joynes.terminalhub.data.ssh

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.Session
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*
import se.joynes.terminalhub.data.logging.AppLogger

class ServerStatusPollerTest {
    private val session: Session = mock()
    private val logger: AppLogger = mock()
    private fun channel(output: String): ChannelExec = mock<ChannelExec>().also {
        whenever(it.inputStream).thenReturn(output.byteInputStream())
    }
    @Test fun parsesCpuMemoryAndDiskAndClosesBothChannels() = runBlocking {
        val top = channel("%Cpu(s): 4.0 us, 25.5 id\nMiB Mem : 2000.0 total, 500.0 free, 1500.0 used")
        val disk = channel("/dev/root 100G 65G 35G 65% /")
        whenever(session.openChannel("exec")).thenReturn(top, disk)
        val status = withTimeout(5000) { ServerStatusPoller(logger).pollStatus(session, 7).first() }
        assertEquals(7L, status.serverId)
        assertEquals(74.5f, status.cpuPercent, .01f)
        assertEquals(75f, status.ramPercent, .01f)
        assertEquals(65f, status.diskPercent, .01f)
        verify(top).disconnect(); verify(disk).disconnect()
        Unit
    }
    @Test fun unrecognizedOutputAndZeroMemoryNeverProduceInvalidPercentages() = runBlocking {
        val top = channel("cpu unavailable\nKiB Mem : 0 total, 5 used\nother")
        val disk = channel("unknown")
        whenever(session.openChannel("exec")).thenReturn(top, disk)
        val status = withTimeout(5000) { ServerStatusPoller(logger).pollStatus(session, 9).first() }
        assertEquals(0f, status.cpuPercent); assertEquals(0f, status.ramPercent); assertEquals(0f, status.diskPercent)
    }
    @Test fun failedExecStillEmitsAnEmptyStatusInsteadOfKillingPolling() = runBlocking {
        whenever(session.openChannel("exec")).thenThrow(IllegalStateException("Disconnected"))
        val status = withTimeout(5000) { ServerStatusPoller(logger).pollStatus(session, 4).first() }
        assertEquals(4L, status.serverId); assertEquals(0f, status.cpuPercent)
        verify(session, times(2)).openChannel("exec")
        Unit
    }
}
