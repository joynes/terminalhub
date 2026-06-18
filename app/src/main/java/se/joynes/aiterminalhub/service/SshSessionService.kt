package se.joynes.aiterminalhub.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import se.joynes.aiterminalhub.MainActivity
import se.joynes.aiterminalhub.R
import se.joynes.aiterminalhub.data.logging.AppLogger
import se.joynes.aiterminalhub.data.logging.LogLevel
import se.joynes.aiterminalhub.data.runtime.AppRuntimeRepository
import se.joynes.aiterminalhub.data.ssh.SshManager
import se.joynes.aiterminalhub.domain.TerminalSessionManager
import javax.inject.Inject

@AndroidEntryPoint
class SshSessionService : Service() {

    @Inject lateinit var logger: AppLogger
    @Inject lateinit var runtimeRepository: AppRuntimeRepository
    @Inject lateinit var sshManager: SshManager
    @Inject lateinit var terminalSessionManager: TerminalSessionManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var notificationJob: Job? = null
    private var explicitStopReason: String? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        explicitStopReason = null
        runtimeRepository.noteForegroundServiceStarted()
        logger.log(LogLevel.INFO, TAG, "Service created; snapshot=${debugSnapshot()}")
        notificationJob = scope.launch {
            sshManager.sessions.collectLatest {
                val count = it.size
                (getSystemService(NotificationManager::class.java))
                    .notify(NOTIFICATION_ID, buildNotification(count))
                logger.log(LogLevel.DEBUG, TAG, "Notification refreshed: sessions=$count snapshot=${debugSnapshot()}")
                maybeStopIfIdle()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logger.log(LogLevel.INFO, TAG, "onStartCommand flags=$flags startId=$startId snapshot=${debugSnapshot()}")
        maybeStopIfIdle()
        return if (sshManager.sessions.value.isEmpty()) START_NOT_STICKY else START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        notificationJob?.cancel()
        val snapshot = debugSnapshot()
        val stopReason = explicitStopReason ?: "destroy-without-explicit-stop"
        runtimeRepository.noteForegroundServiceStopped(stopReason, snapshot)
        logger.log(LogLevel.INFO, TAG, "Service destroyed; reason=$stopReason snapshot=$snapshot")
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    fun debugSnapshot(): String = buildString {
        append("ssh={").append(sshManager.debugSnapshot()).append("}")
        append(",terminals={").append(terminalSessionManager.debugSnapshot()).append("}")
    }

    private fun maybeStopIfIdle() {
        if (sshManager.sessions.value.isEmpty()) {
            explicitStopReason = "idle-no-ssh-sessions"
            logger.log(LogLevel.INFO, TAG, "Stopping idle service; snapshot=${debugSnapshot()}")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun buildNotification(sessionCount: Int = sshManager.sessions.value.size): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = if (sessionCount == 0) {
            "AITerminal ready"
        } else {
            "$sessionCount active SSH session" + if (sessionCount == 1) "" else "s"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("AITerminal")
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "SSH Sessions",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps SSH sessions alive while AITerminal is running"
            }
        )
    }

    companion object {
        private const val TAG = "SshSessionService"
        private const val CHANNEL_ID = "ssh_sessions"
        private const val NOTIFICATION_ID = 1001

    }
}
