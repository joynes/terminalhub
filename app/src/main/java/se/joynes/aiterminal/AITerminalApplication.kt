package se.joynes.aiterminal

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import se.joynes.aiterminal.data.logging.AnrWatchdog
import se.joynes.aiterminal.data.logging.AppLogger
import se.joynes.aiterminal.data.logging.CrashReportStore
import se.joynes.aiterminal.data.logging.LogLevel
import se.joynes.aiterminal.data.runtime.AppRuntimeRepository
import javax.inject.Inject

@HiltAndroidApp
class AITerminalApplication : Application() {
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
