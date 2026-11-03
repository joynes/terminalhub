package se.joynes.terminalhub.data.logging

import android.content.Context
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.*

class AnrRecoveryLogTest {
    @get:Rule val temp = TemporaryFolder()
    @Test fun recoveredAnrIsChunkedLoggedOnceAndRemovedAfterRecovery() {
        val context: Context = mock(); val logger: AppLogger = mock()
        whenever(context.filesDir).thenReturn(temp.root)
        val file = File(temp.root, "crash-reports/pending-anr.txt")
        AnrWatchdog.flushPendingAnr(context, logger)
        verifyNoInteractions(logger)
        file.parentFile!!.mkdirs(); file.writeText("a".repeat(3000) + "tail")
        AnrWatchdog.flushPendingAnr(context, logger)
        val messages = argumentCaptor<String>()
        verify(logger, times(3)).log(eq(LogLevel.ERROR), eq("AnrWatchdog"), messages.capture(), isNull())
        assertEquals("Recovered ANR report from previous launch", messages.allValues[0])
        assertEquals("a".repeat(3000), messages.allValues[1])
        assertEquals("[cont 2] tail", messages.allValues[2]); assertFalse(file.exists())
        clearInvocations(logger)
        AnrWatchdog.flushPendingAnr(context, logger)
        verifyNoInteractions(logger)
    }
    @Test fun blankAndUnreadableMarkersAreDiscardedWithoutReportingAnEmptyAnr() {
        val context: Context = mock(); val logger: AppLogger = mock()
        whenever(context.filesDir).thenReturn(temp.root)
        val file = File(temp.root, "crash-reports/pending-anr.txt")
        file.parentFile!!.mkdirs(); file.writeText(" \n")
        AnrWatchdog.flushPendingAnr(context, logger); assertFalse(file.exists())
        file.mkdirs()
        AnrWatchdog.flushPendingAnr(context, logger); assertFalse(file.exists())
        verifyNoInteractions(logger)
    }
}
