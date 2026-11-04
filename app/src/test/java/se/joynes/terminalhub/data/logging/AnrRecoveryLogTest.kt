package se.joynes.terminalhub.data.logging

import android.content.Context
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.*
import org.mockito.Mockito
import android.os.Looper
import se.joynes.terminalhub.BuildConfig

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
    @Test fun stallReportPersistsInstalledVersionLagAndMainThreadStackForNextLaunch() {
        val context: Context = mock(); whenever(context.filesDir).thenReturn(temp.root)
        val looper: Looper = mock(); whenever(looper.thread).thenReturn(Thread.currentThread())
        Mockito.mockStatic(Looper::class.java).use { platform ->
            platform.`when`<Looper> { Looper.getMainLooper() }.thenReturn(looper)
            AnrWatchdog.writePendingAnr(context, 6500)
            val file = File(temp.root, "crash-reports/pending-anr.txt")
            val report = file.readText()
            assertTrue(report.contains("versionCode=${BuildConfig.VERSION_CODE}"))
            assertTrue(report.contains("versionName=${BuildConfig.VERSION_NAME}"))
            assertTrue(report.contains("detectedLagMs=6500"))
            assertTrue(report.contains("mainThread=${Thread.currentThread().name}"))
            assertTrue(report.contains("Main thread stack:"))
            assertTrue(report.contains("AnrRecoveryLogTest"))
            assertTrue(report.startsWith("timestamp="))
            AnrWatchdog.writePendingAnr(context, 7500)
            assertTrue(file.readText().contains("detectedLagMs=7500"))
            assertFalse(file.readText().contains("detectedLagMs=6500"))
        }
    }
}
