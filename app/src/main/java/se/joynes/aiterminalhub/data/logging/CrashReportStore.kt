package se.joynes.aiterminalhub.data.logging

import android.content.Context
import se.joynes.aiterminalhub.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant

object CrashReportStore {
    private const val CRASH_DIR = "crash-reports"
    private const val PENDING_CRASH_FILE = "pending-crash.txt"
    private const val TAG = "CrashReporter"
    private const val MAX_LOG_CHUNK = 3000

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writePendingCrash(appContext, thread, throwable)
            } catch (_: Throwable) {
            } finally {
                previousHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    fun flushPendingCrash(context: Context, logger: AppLogger) {
        val crashFile = pendingCrashFile(context) ?: return
        val report = try {
            crashFile.readText()
        } catch (_: Throwable) {
            null
        }

        if (report.isNullOrBlank()) {
            crashFile.delete()
            return
        }

        logger.log(LogLevel.ERROR, TAG, "Recovered crash report from previous launch")
        report.chunked(MAX_LOG_CHUNK).forEachIndexed { index, chunk ->
            logger.log(
                LogLevel.ERROR,
                TAG,
                if (index == 0) chunk else "[cont ${index + 1}] $chunk"
            )
        }
        crashFile.delete()
    }

    private fun writePendingCrash(context: Context, thread: Thread, throwable: Throwable) {
        val crashDir = File(context.filesDir, CRASH_DIR).apply { mkdirs() }
        val report = buildString {
            append("timestamp=").append(Instant.now()).append('\n')
            append("versionName=").append(BuildConfig.VERSION_NAME).append('\n')
            append("versionCode=").append(BuildConfig.VERSION_CODE).append('\n')
            append("thread=").append(thread.name).append('\n')
            append('\n')
            append(stackTrace(throwable))
        }
        File(crashDir, PENDING_CRASH_FILE).writeText(report)
    }

    private fun pendingCrashFile(context: Context): File? {
        val file = File(File(context.filesDir, CRASH_DIR), PENDING_CRASH_FILE)
        return file.takeIf { it.exists() }
    }

    private fun stackTrace(throwable: Throwable): String {
        val writer = StringWriter()
        PrintWriter(writer).use { printWriter ->
            throwable.printStackTrace(printWriter)
        }
        return writer.toString()
    }
}
