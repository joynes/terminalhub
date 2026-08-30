package se.joynes.terminalhub.data.runtime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class BackgroundSshMode {
    OFF,
    STARTING,
    ACTIVE,
    STOPPING
}

enum class BackgroundSshCommand {
    NONE,
    START_SERVICE,
    STOP_AND_CLOSE_TRANSPORTS
}

sealed interface BackgroundSshEvent {
    data class UserStart(
        val notificationPermissionGranted: Boolean,
        val activeSshSessionCount: Int
    ) : BackgroundSshEvent

    data object SshTabConnected : BackgroundSshEvent
    data object ServiceStarted : BackgroundSshEvent
    data object UserStop : BackgroundSshEvent
    data object LastSshTabClosed : BackgroundSshEvent
    data object PermissionDenied : BackgroundSshEvent
    data object ServiceStopped : BackgroundSshEvent
    data object ProcessStarted : BackgroundSshEvent
}

data class BackgroundSshTransition(
    val mode: BackgroundSshMode,
    val command: BackgroundSshCommand = BackgroundSshCommand.NONE
)

/** Pure policy reducer. SSH connections alone can never start the foreground service. */
fun reduceBackgroundSshMode(
    current: BackgroundSshMode,
    event: BackgroundSshEvent
): BackgroundSshTransition = when (event) {
    is BackgroundSshEvent.UserStart -> when {
        !event.notificationPermissionGranted -> BackgroundSshTransition(BackgroundSshMode.OFF)
        current == BackgroundSshMode.OFF -> BackgroundSshTransition(
            BackgroundSshMode.STARTING,
            BackgroundSshCommand.START_SERVICE
        )
        else -> BackgroundSshTransition(current)
    }
    BackgroundSshEvent.SshTabConnected -> BackgroundSshTransition(current)
    BackgroundSshEvent.ServiceStarted -> BackgroundSshTransition(BackgroundSshMode.ACTIVE)
    BackgroundSshEvent.UserStop -> if (current == BackgroundSshMode.OFF) {
        BackgroundSshTransition(BackgroundSshMode.OFF, BackgroundSshCommand.STOP_AND_CLOSE_TRANSPORTS)
    } else {
        BackgroundSshTransition(BackgroundSshMode.STOPPING, BackgroundSshCommand.STOP_AND_CLOSE_TRANSPORTS)
    }
    BackgroundSshEvent.LastSshTabClosed -> BackgroundSshTransition(current)
    BackgroundSshEvent.PermissionDenied,
    BackgroundSshEvent.ServiceStopped,
    BackgroundSshEvent.ProcessStarted -> BackgroundSshTransition(BackgroundSshMode.OFF)
}

@Singleton
class BackgroundSshModeController @Inject constructor() {
    private val _mode = MutableStateFlow(BackgroundSshMode.OFF)
    val mode: StateFlow<BackgroundSshMode> = _mode.asStateFlow()

    @Synchronized
    fun dispatch(event: BackgroundSshEvent): BackgroundSshTransition {
        val transition = reduceBackgroundSshMode(_mode.value, event)
        _mode.value = transition.mode
        return transition
    }
}
