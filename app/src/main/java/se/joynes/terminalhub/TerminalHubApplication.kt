package se.joynes.terminalhub

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import se.joynes.terminalhub.data.logging.AnrWatchdog
import se.joynes.terminalhub.data.logging.AppLogger
import se.joynes.terminalhub.data.logging.CrashReportStore
import se.joynes.terminalhub.data.logging.LogLevel
import se.joynes.terminalhub.data.runtime.AppRuntimeRepository
import javax.inject.Inject

@HiltAndroidApp
class TerminalHubApplication : Application() {
    @Inject lateinit var appLogger: AppLogger
    @Inject lateinit var runtimeRepository: AppRuntimeRepository

    override fun onCreate() {
        super.onCreate()
        CrashReportStore.install(this)
        AnrWatchdog.install(this)
        CrashReportStore.flushPendingCrash(this, appLogger)
        AnrWatchdog.flushPendingAnr(this, appLogger)
        val runtimeState = runtimeRepository.onProcessStarted()
        val tag = "AppRuntime"
        if (runtimeState.recoveryPending) {
            appLogger.log(
                LogLevel.WARN,
                tag,
                "Process restart detected; ${runtimeState.lastProcessRestartReason}"
            )
        } else {
            appLogger.log(LogLevel.INFO, tag, "Process started cleanly")
        }
    }
}
