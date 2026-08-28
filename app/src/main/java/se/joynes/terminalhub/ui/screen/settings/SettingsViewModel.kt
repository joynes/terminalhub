package se.joynes.terminalhub.ui.screen.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import se.joynes.terminalhub.data.runtime.AppRuntimeRepository
import se.joynes.terminalhub.data.runtime.BackgroundSshCommand
import se.joynes.terminalhub.data.runtime.BackgroundSshEvent
import se.joynes.terminalhub.data.runtime.BackgroundSshModeController
import se.joynes.terminalhub.data.settings.AppSettingsRepository
import se.joynes.terminalhub.data.settings.BackgroundKeepaliveProfile
import se.joynes.terminalhub.data.settings.BackgroundKeepaliveScope
import se.joynes.terminalhub.domain.TerminalSessionManager
import se.joynes.terminalhub.service.BackgroundSshService
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: AppSettingsRepository,
    private val runtimeRepository: AppRuntimeRepository,
    private val sessionManager: TerminalSessionManager,
    private val backgroundSshModeController: BackgroundSshModeController
) : ViewModel() {
    val settings = settingsRepository.settings
    val runtimeState = runtimeRepository.state
    val backgroundSshMode = backgroundSshModeController.mode

    fun startBackgroundSsh(notificationPermissionGranted: Boolean): BackgroundSshStartResult {
        settingsRepository.setBackgroundSshRecommendationHandled()
        val transition = backgroundSshModeController.dispatch(
            BackgroundSshEvent.UserStart(
                notificationPermissionGranted = notificationPermissionGranted,
                activeSshSessionCount = runtimeRepository.state.value.remoteProjectIds.size
            )
        )
        if (!notificationPermissionGranted) {
            settingsRepository.setKeepSshActiveInBackground(false)
            return BackgroundSshStartResult.NOTIFICATION_PERMISSION_REQUIRED
        }
        if (transition.command != BackgroundSshCommand.START_SERVICE) {
            settingsRepository.setKeepSshActiveInBackground(false)
            return BackgroundSshStartResult.NO_ACTIVE_SSH_SESSIONS
        }
        return runCatching {
            settingsRepository.setKeepSshActiveInBackground(true)
            BackgroundSshService.requestStart(context)
        }.fold(
            onSuccess = { BackgroundSshStartResult.STARTED },
            onFailure = {
                settingsRepository.setKeepSshActiveInBackground(false)
                backgroundSshModeController.dispatch(BackgroundSshEvent.ServiceStopped)
                BackgroundSshStartResult.START_FAILED
            }
        )
    }

    fun notificationPermissionDenied() {
        settingsRepository.setKeepSshActiveInBackground(false)
        backgroundSshModeController.dispatch(BackgroundSshEvent.PermissionDenied)
    }

    fun stopBackgroundSsh() {
        settingsRepository.setKeepSshActiveInBackground(false)
        val transition = backgroundSshModeController.dispatch(BackgroundSshEvent.UserStop)
        if (runtimeRepository.state.value.foregroundServiceRunning) {
            BackgroundSshService.requestStop(
                context,
                BackgroundSshService.STOP_REASON_USER_SETTINGS,
                closeTransports = transition.command == BackgroundSshCommand.STOP_AND_CLOSE_TRANSPORTS
            )
        } else if (transition.command == BackgroundSshCommand.STOP_AND_CLOSE_TRANSPORTS) {
            sessionManager.closeAllRemoteTransports()
            backgroundSshModeController.dispatch(BackgroundSshEvent.ServiceStopped)
        }
    }

    fun setPreferFastResume(enabled: Boolean) {
        settingsRepository.setPreferFastResume(enabled)
    }

    fun setExecuteTextInputOnSend(enabled: Boolean) {
        settingsRepository.setExecuteTextInputOnSend(enabled)
    }

    fun setSshKeepaliveEnabled(enabled: Boolean) {
        settingsRepository.setSshKeepaliveEnabled(enabled)
    }

    fun setBackgroundKeepaliveProfile(profile: BackgroundKeepaliveProfile) {
        settingsRepository.setBackgroundKeepaliveProfile(profile)
    }

    fun setBackgroundKeepaliveScope(scope: BackgroundKeepaliveScope) {
        settingsRepository.setBackgroundKeepaliveScope(scope)
    }

    fun setKeyBarRows(rows: List<List<String>>) {
        settingsRepository.setKeyBarRows(rows)
    }
}

enum class BackgroundSshStartResult {
    STARTED,
    NO_ACTIVE_SSH_SESSIONS,
    NOTIFICATION_PERMISSION_REQUIRED,
    START_FAILED
}
