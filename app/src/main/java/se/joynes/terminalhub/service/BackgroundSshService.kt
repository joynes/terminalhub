package se.joynes.terminalhub.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import se.joynes.terminalhub.MainActivity
import se.joynes.terminalhub.R
import se.joynes.terminalhub.data.logging.AppLogger
import se.joynes.terminalhub.data.logging.LogLevel
import se.joynes.terminalhub.data.runtime.AppRuntimeRepository
import se.joynes.terminalhub.data.runtime.BackgroundSshEvent
import se.joynes.terminalhub.data.runtime.BackgroundSshModeController
import se.joynes.terminalhub.data.settings.AppSettingsRepository
import se.joynes.terminalhub.domain.TerminalSessionManager
import javax.inject.Inject

@AndroidEntryPoint
class BackgroundSshService : Service() {
    @Inject lateinit var runtimeRepository: AppRuntimeRepository
    @Inject lateinit var settingsRepository: AppSettingsRepository
    @Inject lateinit var sessionManager: TerminalSessionManager
    @Inject lateinit var modeController: BackgroundSshModeController
    @Inject lateinit var logger: AppLogger

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var countJob: Job? = null
    private var foregroundStarted = false
    private var stopRecorded = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startFromUserAction()
            ACTION_STOP -> {
                modeController.dispatch(BackgroundSshEvent.UserStop)
                stopAndClose(
                    reason = intent.getStringExtra(EXTRA_REASON) ?: STOP_REASON_NOTIFICATION,
                    closeTransports = intent.getBooleanExtra(EXTRA_CLOSE_TRANSPORTS, true)
                )
            }
            ACTION_REFRESH -> if (foregroundStarted) updateNotification(activeSshCount())
            else -> stopWithoutRestart(STOP_REASON_MISSING_ACTION, closeTransports = false)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        countJob?.cancel()
        serviceScope.cancel()
        settingsRepository.setKeepSshActiveInBackground(false)
        modeController.dispatch(BackgroundSshEvent.ServiceStopped)
        if (!stopRecorded && foregroundStarted) {
            runtimeRepository.noteForegroundServiceStopped(STOP_REASON_SERVICE_DESTROYED, sessionManager.debugSnapshot())
        }
        super.onDestroy()
    }

    private fun startFromUserAction() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            modeController.dispatch(BackgroundSshEvent.PermissionDenied)
            stopWithoutRestart(STOP_REASON_NOTIFICATION_PERMISSION, closeTransports = false)
            return
        }
        val count = activeSshCount()
        if (count == 0) {
            stopWithoutRestart(STOP_REASON_NO_ACTIVE_SESSIONS, closeTransports = false)
            return
        }

        val notification = buildNotification(count)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        foregroundStarted = true
        settingsRepository.setKeepSshActiveInBackground(true)
        runtimeRepository.noteForegroundServiceStarted()
        modeController.dispatch(BackgroundSshEvent.ServiceStarted)
        logger.log(LogLevel.INFO, TAG, "User-started background SSH enabled; sessions=$count")

        countJob?.cancel()
        countJob = serviceScope.launch {
            runtimeRepository.state
                .map { it.remoteProjectIds.size }
                .distinctUntilChanged()
                .collect { sessionCount ->
                    if (sessionCount == 0) {
                        stopAndClose(STOP_REASON_LAST_SESSION_CLOSED, closeTransports = false)
                    } else {
                        updateNotification(sessionCount)
                    }
                }
        }
    }

    private fun stopAndClose(reason: String, closeTransports: Boolean) {
        if (closeTransports) sessionManager.closeAllRemoteTransports()
        stopWithoutRestart(reason, closeTransports)
    }

    private fun stopWithoutRestart(reason: String, closeTransports: Boolean) {
        if (stopRecorded) return
        stopRecorded = true
        settingsRepository.setKeepSshActiveInBackground(false)
        modeController.dispatch(BackgroundSshEvent.ServiceStopped)
        runtimeRepository.noteForegroundServiceStopped(reason, sessionManager.debugSnapshot())
        logger.log(
            LogLevel.INFO,
            TAG,
            "Background SSH stopped; reason=$reason closeTransports=$closeTransports"
        )
        if (foregroundStarted) stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun activeSshCount(): Int = runtimeRepository.state.value.remoteProjectIds.size

    private fun updateNotification(count: Int) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(count))
    }

    private fun buildNotification(count: Int): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_OPEN
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val stopIntent = Intent(this, BackgroundSshService::class.java).apply {
            action = ACTION_STOP
            putExtra(EXTRA_REASON, STOP_REASON_NOTIFICATION)
            putExtra(EXTRA_CLOSE_TRANSPORTS, true)
        }
        val immutableFlag = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val openPendingIntent = PendingIntent.getActivity(this, 0, openIntent, immutableFlag)
        val stopPendingIntent = PendingIntent.getService(this, 1, stopIntent, immutableFlag)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_terminal)
            .setContentTitle("TerminalHub SSH active")
            .setContentText(backgroundSshNotificationText(count))
            .setContentIntent(openPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .addAction(0, "Stop", stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Active SSH connections",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shown only when you choose to keep SSH connections active in the background."
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START = "se.joynes.terminalhub.backgroundssh.START"
        const val ACTION_STOP = "se.joynes.terminalhub.backgroundssh.STOP"
        const val ACTION_REFRESH = "se.joynes.terminalhub.backgroundssh.REFRESH"
        const val ACTION_OPEN = "se.joynes.terminalhub.backgroundssh.OPEN"
        const val STOP_REASON_USER_SETTINGS = "user_stopped_from_settings"
        const val STOP_REASON_NOTIFICATION = "user_stopped_from_notification"
        const val STOP_REASON_LAST_SESSION_CLOSED = "last_ssh_session_closed"
        const val STOP_REASON_SERVICE_DESTROYED = "service_destroyed"
        const val STOP_REASON_NOTIFICATION_PERMISSION = "notification_permission_denied"
        const val STOP_REASON_NO_ACTIVE_SESSIONS = "no_active_ssh_sessions"
        const val STOP_REASON_MISSING_ACTION = "missing_or_unknown_action"

        private const val EXTRA_REASON = "stop_reason"
        private const val EXTRA_CLOSE_TRANSPORTS = "close_transports"
        private const val CHANNEL_ID = "background_ssh"
        private const val NOTIFICATION_ID = 4101
        private const val TAG = "BackgroundSshService"

        fun requestStart(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, BackgroundSshService::class.java).setAction(ACTION_START)
            )
        }

        fun requestStop(context: Context, reason: String, closeTransports: Boolean) {
            context.startService(
                Intent(context, BackgroundSshService::class.java)
                    .setAction(ACTION_STOP)
                    .putExtra(EXTRA_REASON, reason)
                    .putExtra(EXTRA_CLOSE_TRANSPORTS, closeTransports)
            )
        }
    }
}

internal fun backgroundSshNotificationText(activeSshCount: Int): String =
    if (activeSshCount == 1) "1 active SSH connection" else "$activeSshCount active SSH connections"
