package se.joynes.aiterminalhub

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import se.joynes.aiterminalhub.data.logging.AnrWatchdog
import se.joynes.aiterminalhub.data.logging.AppLogger
import se.joynes.aiterminalhub.data.logging.CrashReportStore
import javax.inject.Inject

@HiltAndroidApp
class AITerminalHubApplication : Application() {
    @Inject lateinit var appLogger: AppLogger

    override fun onCreate() {
        super.onCreate()
        CrashReportStore.install(this)
        AnrWatchdog.install(this)
        CrashReportStore.flushPendingCrash(this, appLogger)
        AnrWatchdog.flushPendingAnr(this, appLogger)
    }
}
