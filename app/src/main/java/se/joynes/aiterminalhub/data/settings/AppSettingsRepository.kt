package se.joynes.aiterminalhub.data.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class AppSettings(
    val preferFastResume: Boolean = true,
    val sshKeepaliveEnabled: Boolean = true
)

@Singleton
class AppSettingsRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(
        AppSettings(
            preferFastResume = prefs.getBoolean(KEY_FAST_RESUME, true),
            sshKeepaliveEnabled = prefs.getBoolean(KEY_SSH_KEEPALIVE, true)
        )
    )
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun setPreferFastResume(enabled: Boolean) {
        update(_settings.value.copy(preferFastResume = enabled))
    }

    fun setSshKeepaliveEnabled(enabled: Boolean) {
        update(_settings.value.copy(sshKeepaliveEnabled = enabled))
    }

    private fun update(next: AppSettings) {
        _settings.value = next
        prefs.edit()
            .putBoolean(KEY_FAST_RESUME, next.preferFastResume)
            .putBoolean(KEY_SSH_KEEPALIVE, next.sshKeepaliveEnabled)
            .apply()
    }

    companion object {
        private const val KEY_FAST_RESUME = "prefer_fast_resume"
        private const val KEY_SSH_KEEPALIVE = "ssh_keepalive_enabled"
    }
}
