package se.joynes.terminalhub.data.logging

import android.content.Context
import java.io.File
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.*

class CrashReportStoreTest {
    @get:Rule val temp = TemporaryFolder()
    private val context: Context = mock()
    private val logger: AppLogger = mock()
    private var previous: Thread.UncaughtExceptionHandler? = null
    private val pending get() = File(temp.root, "crash-reports/pending-crash.txt")
    @Before fun setup() {
        whenever(context.filesDir).thenReturn(temp.root)
        whenever(context.applicationContext).thenReturn(context)
        previous = Thread.getDefaultUncaughtExceptionHandler()
    }
    @After fun restoreHandler() { Thread.setDefaultUncaughtExceptionHandler(previous) }

    @Test fun crashIsPersistedBeforeDelegatingAndRecoveredOnlyOnce() {
        val error = IllegalStateException("fixture crash", IllegalArgumentException("root cause"))
        val thread = Thread("fixture-worker")
        var delegated = false
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            assertSame(thread, t); assertSame(error, e)
            assertTrue(pending.exists()); delegated = true
        }
        CrashReportStore.install(context)
        Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(thread, error)
        assertTrue(delegated)
        val text = pending.readText()
        assertTrue(text.contains("timestamp=")); assertTrue(text.contains("versionCode="))
        assertTrue(text.contains("thread=fixture-worker")); assertTrue(text.contains("root cause"))
        CrashReportStore.flushPendingCrash(context, logger)
        assertFalse(pending.exists())
        val reports = argumentCaptor<String>()
        verify(logger, atLeastOnce()).log(eq(LogLevel.ERROR), eq("CrashReporter"), reports.capture(), isNull())
        assertTrue(reports.allValues.any { it.contains("fixture crash") })
        clearInvocations(logger)
        CrashReportStore.flushPendingCrash(context, logger)
        verifyNoInteractions(logger)
    }
    @Test fun blankCrashIsDiscardedWithoutLoggingAndLongReportsAreChunkedInOrder() {
        pending.parentFile!!.mkdirs(); pending.writeText(" \n")
        CrashReportStore.flushPendingCrash(context, logger)
        assertFalse(pending.exists()); verifyNoInteractions(logger)
        val report = "a".repeat(3000) + "b".repeat(3000) + "tail"
        pending.writeText(report)
        CrashReportStore.flushPendingCrash(context, logger)
        val messages = argumentCaptor<String>()
        verify(logger, times(4)).log(eq(LogLevel.ERROR), eq("CrashReporter"), messages.capture(), isNull())
        assertEquals("a".repeat(3000), messages.allValues[1])
        assertEquals("[cont 2] " + "b".repeat(3000), messages.allValues[2])
        assertEquals("[cont 3] tail", messages.allValues[3]); assertFalse(pending.exists())
    }
    @Test fun persistenceFailureStillDelegatesToPreviousHandler() {
        val unusable = temp.newFile("not-a-directory")
        whenever(context.filesDir).thenReturn(unusable)
        var delegated = false
        Thread.setDefaultUncaughtExceptionHandler { _, _ -> delegated = true }
        CrashReportStore.install(context)
        Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(Thread.currentThread(), RuntimeException("fixture"))
        assertTrue(delegated)
    }
    @Test fun missingOrUnreadableReportDoesNotLogAnEmptyCrash() {
        CrashReportStore.flushPendingCrash(context, logger)
        pending.mkdirs() // directory readText fails; the empty marker should be cleaned up
        CrashReportStore.flushPendingCrash(context, logger)
        assertFalse(pending.exists()); verifyNoInteractions(logger)
    }
    @Test fun crashWithoutAnExistingHandlerIsStillSaved() {
        Thread.setDefaultUncaughtExceptionHandler(null)
        CrashReportStore.install(context)
        Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(Thread.currentThread(), RuntimeException("standalone"))
        assertTrue(pending.readText().contains("standalone"))
    }
}
