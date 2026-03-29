package se.joynes.aiterminalhub.data.logging

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import se.joynes.aiterminalhub.BuildConfig
import java.io.File
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

object AnrWatchdog {
    private const val CRASH_DIR = "crash-reports"
    private const val PENDING_ANR_FILE = "pending-anr.txt"
    private const val TAG = "AnrWatchdog"
    private const val MAX_LOG_CHUNK = 3000
    private const val ANR_TIMEOUT_MS = 5000L
    private const val POLL_INTERVAL_MS = 1000L

    private val lastMainThreadBeat = AtomicLong(0L)
    @Volatile private var installed = false
    @Volatile private var reportedForCurrentFreeze = false

    fun install(context: Context) {
        if (installed) return
        installed = true

        val appContext = context.applicationContext
        val mainHandler = Handler(Looper.getMainLooper())

        fun scheduleBeat() {
            mainHandler.post {
                lastMainThreadBeat.set(SystemClock.uptimeMillis())
                reportedForCurrentFreeze = false
                scheduleBeat()
            }
        }

        lastMainThreadBeat.set(SystemClock.uptimeMillis())
        scheduleBeat()

        Thread({
            while (true) {
                try {
                    Thread.sleep(POLL_INTERVAL_MS)
                    val lagMs = SystemClock.uptimeMillis() - lastMainThreadBeat.get()
                    if (lagMs >= ANR_TIMEOUT_MS && !reportedForCurrentFreeze) {
                        reportedForCurrentFreeze = true
                        writePendingAnr(appContext, lagMs)
                    }
                } catch (_: InterruptedException) {
                    return@Thread
                } catch (_: Throwable) {
                }
            }
        }, "aiterminalhub-anr-watchdog").apply {
            isDaemon = true
            start()
        }
    }

    fun flushPendingAnr(context: Context, logger: AppLogger) {
        val anrFile = pendingAnrFile(context) ?: return
        val report = try {
            anrFile.readText()
        } catch (_: Throwable) {
            null
        }

        if (report.isNullOrBlank()) {
            anrFile.delete()
            return
        }

        logger.log(LogLevel.ERROR, TAG, "Recovered ANR report from previous launch")
        report.chunked(MAX_LOG_CHUNK).forEachIndexed { index, chunk ->
            logger.log(
                LogLevel.ERROR,
                TAG,
                if (index == 0) chunk else "[cont ${index + 1}] $chunk"
            )
        }
        anrFile.delete()
    }

    private fun writePendingAnr(context: Context, lagMs: Long) {
        val crashDir = File(context.filesDir, CRASH_DIR).apply { mkdirs() }
        val report = buildString {
            append("timestamp=").append(Instant.now()).append('\n')
            append("versionName=").append(BuildConfig.VERSION_NAME).append('\n')
            append("versionCode=").append(BuildConfig.VERSION_CODE).append('\n')
            append("detectedLagMs=").append(lagMs).append('\n')
            append("mainThread=").append(Looper.getMainLooper().thread.name).append('\n')
            append('\n')
            append("Main thread stack:\n")
            Looper.getMainLooper().thread.stackTrace.forEach { element ->
                append("    at ").append(element.toString()).append('\n')
            }
        }
        File(crashDir, PENDING_ANR_FILE).writeText(report)
    }

    private fun pendingAnrFile(context: Context): File? {
        val file = File(File(context.filesDir, CRASH_DIR), PENDING_ANR_FILE)
        return file.takeIf { it.exists() }
    }
}
