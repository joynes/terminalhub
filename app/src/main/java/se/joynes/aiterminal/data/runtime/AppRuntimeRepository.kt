package se.joynes.aiterminal.data.runtime

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class AppRuntimeState(
    val processToken: String = "",
    val processStartedAt: Long = 0L,
    val appInForeground: Boolean = false,
    val lastForegroundAt: Long? = null,
    val lastBackgroundAt: Long? = null,
    val remoteProjectIds: Set<Long> = emptySet(),
    val localProjectIds: Set<Long> = emptySet(),
    val activeProjectId: Long? = null,
    val foregroundServiceRunning: Boolean = false,
    val lastServiceStartedAt: Long? = null,
    val lastServiceStopAt: Long? = null,
    val lastServiceStopReason: String? = null,
    val lastServiceStopSnapshot: String? = null,
    val recoveryPending: Boolean = false,
    val recoveryRemoteProjectIds: Set<Long> = emptySet(),
    val recoveryLocalProjectIds: Set<Long> = emptySet(),
    val recoveryActiveProjectId: Long? = null,
    val lastProcessRestartReason: String? = null,
    val lastSshDisconnectProjectId: Long? = null,
    val lastSshDisconnectSummary: String? = null,
    val lastSshDisconnectAt: Long? = null
)

@Singleton
class AppRuntimeRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("app_runtime", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(readState())
    val state: StateFlow<AppRuntimeState> = _state.asStateFlow()

    fun onProcessStarted(): AppRuntimeState {
        val previous = readState()
        val now = System.currentTimeMillis()
        val hadRecoverableState = previous.foregroundServiceRunning ||
            previous.remoteProjectIds.isNotEmpty() ||
            previous.localProjectIds.isNotEmpty()
        val restartReason = if (hadRecoverableState) {
            buildString {
                append("previous_process_lost")
                append(" serviceRunning=").append(previous.foregroundServiceRunning)
                append(" remoteProjects=").append(previous.remoteProjectIds.sorted())
                append(" localProjects=").append(previous.localProjectIds.sorted())
                append(" activeProject=").append(previous.activeProjectId)
                append(" lastBackgroundAt=").append(previous.lastBackgroundAt)
                append(" lastServiceStopReason=").append(previous.lastServiceStopReason)
                append(" lastServiceStopAt=").append(previous.lastServiceStopAt)
            }
        } else {
            null
        }
        val next = previous.copy(
            processToken = UUID.randomUUID().toString(),
            processStartedAt = now,
            appInForeground = false,
            remoteProjectIds = emptySet(),
            localProjectIds = emptySet(),
            foregroundServiceRunning = false,
            recoveryPending = hadRecoverableState,
            recoveryRemoteProjectIds = previous.remoteProjectIds,
            recoveryLocalProjectIds = previous.localProjectIds,
            recoveryActiveProjectId = previous.activeProjectId,
            lastProcessRestartReason = restartReason
        )
        persist(next)
        return next
    }

    fun noteAppForeground() {
        update(_state.value.copy(appInForeground = true, lastForegroundAt = System.currentTimeMillis()))
    }

    fun noteAppBackground() {
        update(_state.value.copy(appInForeground = false, lastBackgroundAt = System.currentTimeMillis()))
    }

    fun noteForegroundServiceRunning(running: Boolean) {
        update(_state.value.copy(foregroundServiceRunning = running))
    }

    fun noteForegroundServiceStarted() {
        update(
            _state.value.copy(
                foregroundServiceRunning = true,
                lastServiceStartedAt = System.currentTimeMillis()
            )
        )
    }

    fun noteForegroundServiceStopped(reason: String, snapshot: String?) {
        update(
            _state.value.copy(
                foregroundServiceRunning = false,
                lastServiceStopAt = System.currentTimeMillis(),
                lastServiceStopReason = reason,
                lastServiceStopSnapshot = snapshot
            )
        )
    }

    fun noteSessions(
        remoteProjectIds: Set<Long>,
        localProjectIds: Set<Long>,
        activeProjectId: Long?
    ) {
        val shouldClearRecovery = remoteProjectIds.isNotEmpty() || localProjectIds.isNotEmpty()
        update(
            _state.value.copy(
                remoteProjectIds = remoteProjectIds,
                localProjectIds = localProjectIds,
                activeProjectId = activeProjectId,
                recoveryPending = if (shouldClearRecovery) false else _state.value.recoveryPending
            )
        )
    }

    fun noteSshDisconnect(projectId: Long, summary: String) {
        update(
            _state.value.copy(
                lastSshDisconnectProjectId = projectId,
                lastSshDisconnectSummary = summary,
                lastSshDisconnectAt = System.currentTimeMillis()
            )
        )
    }

    fun clearSessionState() {
        update(
            _state.value.copy(
                remoteProjectIds = emptySet(),
                localProjectIds = emptySet(),
                activeProjectId = null,
                foregroundServiceRunning = false,
                recoveryPending = false,
                recoveryRemoteProjectIds = emptySet(),
                recoveryLocalProjectIds = emptySet(),
                recoveryActiveProjectId = null,
                lastSshDisconnectProjectId = null,
                lastSshDisconnectSummary = null,
                lastSshDisconnectAt = null
            )
        )
    }

    private fun update(next: AppRuntimeState) {
        persist(next)
    }

    private fun persist(next: AppRuntimeState) {
        _state.value = next
        prefs.edit()
            .putString(KEY_PROCESS_TOKEN, next.processToken)
            .putLong(KEY_PROCESS_STARTED_AT, next.processStartedAt)
            .putBoolean(KEY_APP_IN_FOREGROUND, next.appInForeground)
            .putLong(KEY_LAST_FOREGROUND_AT, next.lastForegroundAt ?: -1L)
            .putLong(KEY_LAST_BACKGROUND_AT, next.lastBackgroundAt ?: -1L)
            .putStringSet(KEY_REMOTE_PROJECT_IDS, next.remoteProjectIds.map { it.toString() }.toSet())
            .putStringSet(KEY_LOCAL_PROJECT_IDS, next.localProjectIds.map { it.toString() }.toSet())
            .putLong(KEY_ACTIVE_PROJECT_ID, next.activeProjectId ?: -1L)
            .putBoolean(KEY_FOREGROUND_SERVICE_RUNNING, next.foregroundServiceRunning)
            .putLong(KEY_LAST_SERVICE_STARTED_AT, next.lastServiceStartedAt ?: -1L)
            .putLong(KEY_LAST_SERVICE_STOP_AT, next.lastServiceStopAt ?: -1L)
            .putString(KEY_LAST_SERVICE_STOP_REASON, next.lastServiceStopReason)
            .putString(KEY_LAST_SERVICE_STOP_SNAPSHOT, next.lastServiceStopSnapshot)
            .putBoolean(KEY_RECOVERY_PENDING, next.recoveryPending)
            .putStringSet(KEY_RECOVERY_REMOTE_PROJECT_IDS, next.recoveryRemoteProjectIds.map { it.toString() }.toSet())
            .putStringSet(KEY_RECOVERY_LOCAL_PROJECT_IDS, next.recoveryLocalProjectIds.map { it.toString() }.toSet())
            .putLong(KEY_RECOVERY_ACTIVE_PROJECT_ID, next.recoveryActiveProjectId ?: -1L)
            .putString(KEY_LAST_PROCESS_RESTART_REASON, next.lastProcessRestartReason)
            .putLong(KEY_LAST_SSH_DISCONNECT_PROJECT_ID, next.lastSshDisconnectProjectId ?: -1L)
            .putString(KEY_LAST_SSH_DISCONNECT_SUMMARY, next.lastSshDisconnectSummary)
            .putLong(KEY_LAST_SSH_DISCONNECT_AT, next.lastSshDisconnectAt ?: -1L)
            .apply()
    }

    private fun readState(): AppRuntimeState =
        AppRuntimeState(
            processToken = prefs.getString(KEY_PROCESS_TOKEN, "").orEmpty(),
            processStartedAt = prefs.getLong(KEY_PROCESS_STARTED_AT, 0L),
            appInForeground = prefs.getBoolean(KEY_APP_IN_FOREGROUND, false),
            lastForegroundAt = prefs.getLong(KEY_LAST_FOREGROUND_AT, -1L).takeIf { it >= 0 },
            lastBackgroundAt = prefs.getLong(KEY_LAST_BACKGROUND_AT, -1L).takeIf { it >= 0 },
            remoteProjectIds = prefs.getStringSet(KEY_REMOTE_PROJECT_IDS, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet(),
            localProjectIds = prefs.getStringSet(KEY_LOCAL_PROJECT_IDS, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet(),
            activeProjectId = prefs.getLong(KEY_ACTIVE_PROJECT_ID, -1L).takeIf { it >= 0 },
            foregroundServiceRunning = prefs.getBoolean(KEY_FOREGROUND_SERVICE_RUNNING, false),
            lastServiceStartedAt = prefs.getLong(KEY_LAST_SERVICE_STARTED_AT, -1L).takeIf { it >= 0 },
            lastServiceStopAt = prefs.getLong(KEY_LAST_SERVICE_STOP_AT, -1L).takeIf { it >= 0 },
            lastServiceStopReason = prefs.getString(KEY_LAST_SERVICE_STOP_REASON, null),
            lastServiceStopSnapshot = prefs.getString(KEY_LAST_SERVICE_STOP_SNAPSHOT, null),
            recoveryPending = prefs.getBoolean(KEY_RECOVERY_PENDING, false),
            recoveryRemoteProjectIds = prefs.getStringSet(KEY_RECOVERY_REMOTE_PROJECT_IDS, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet(),
            recoveryLocalProjectIds = prefs.getStringSet(KEY_RECOVERY_LOCAL_PROJECT_IDS, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet(),
            recoveryActiveProjectId = prefs.getLong(KEY_RECOVERY_ACTIVE_PROJECT_ID, -1L).takeIf { it >= 0 },
            lastProcessRestartReason = prefs.getString(KEY_LAST_PROCESS_RESTART_REASON, null),
            lastSshDisconnectProjectId = prefs.getLong(KEY_LAST_SSH_DISCONNECT_PROJECT_ID, -1L).takeIf { it >= 0 },
            lastSshDisconnectSummary = prefs.getString(KEY_LAST_SSH_DISCONNECT_SUMMARY, null),
            lastSshDisconnectAt = prefs.getLong(KEY_LAST_SSH_DISCONNECT_AT, -1L).takeIf { it >= 0 }
        )

    companion object {
        private const val KEY_PROCESS_TOKEN = "process_token"
        private const val KEY_PROCESS_STARTED_AT = "process_started_at"
        private const val KEY_APP_IN_FOREGROUND = "app_in_foreground"
        private const val KEY_LAST_FOREGROUND_AT = "last_foreground_at"
        private const val KEY_LAST_BACKGROUND_AT = "last_background_at"
        private const val KEY_REMOTE_PROJECT_IDS = "remote_project_ids"
        private const val KEY_LOCAL_PROJECT_IDS = "local_project_ids"
        private const val KEY_ACTIVE_PROJECT_ID = "active_project_id"
        private const val KEY_FOREGROUND_SERVICE_RUNNING = "foreground_service_running"
        private const val KEY_LAST_SERVICE_STARTED_AT = "last_service_started_at"
        private const val KEY_LAST_SERVICE_STOP_AT = "last_service_stop_at"
        private const val KEY_LAST_SERVICE_STOP_REASON = "last_service_stop_reason"
        private const val KEY_LAST_SERVICE_STOP_SNAPSHOT = "last_service_stop_snapshot"
        private const val KEY_RECOVERY_PENDING = "recovery_pending"
        private const val KEY_RECOVERY_REMOTE_PROJECT_IDS = "recovery_remote_project_ids"
        private const val KEY_RECOVERY_LOCAL_PROJECT_IDS = "recovery_local_project_ids"
        private const val KEY_RECOVERY_ACTIVE_PROJECT_ID = "recovery_active_project_id"
        private const val KEY_LAST_PROCESS_RESTART_REASON = "last_process_restart_reason"
        private const val KEY_LAST_SSH_DISCONNECT_PROJECT_ID = "last_ssh_disconnect_project_id"
        private const val KEY_LAST_SSH_DISCONNECT_SUMMARY = "last_ssh_disconnect_summary"
        private const val KEY_LAST_SSH_DISCONNECT_AT = "last_ssh_disconnect_at"
    }
}
