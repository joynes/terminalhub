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
    val sshKeepaliveEnabled: Boolean = true,
    val backgroundKeepaliveProfile: BackgroundKeepaliveProfile = BackgroundKeepaliveProfile.BALANCED,
    val backgroundKeepaliveScope: BackgroundKeepaliveScope = BackgroundKeepaliveScope.ACTIVE_TAB_ONLY
)

enum class BackgroundKeepaliveProfile {
    AGGRESSIVE,
    BALANCED,
    BATTERY_SAVER
}

enum class BackgroundKeepaliveScope {
    ALL_SESSIONS,
    ACTIVE_TAB_ONLY
}

@Singleton
class AppSettingsRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(
        AppSettings(
            preferFastResume = prefs.getBoolean(KEY_FAST_RESUME, true),
            sshKeepaliveEnabled = prefs.getBoolean(KEY_SSH_KEEPALIVE, true),
            backgroundKeepaliveProfile = prefs.getString(KEY_BACKGROUND_KEEPALIVE_PROFILE, BackgroundKeepaliveProfile.BALANCED.name)
                ?.let { runCatching { BackgroundKeepaliveProfile.valueOf(it) }.getOrNull() }
                ?: BackgroundKeepaliveProfile.BALANCED,
            backgroundKeepaliveScope = prefs.getString(KEY_BACKGROUND_KEEPALIVE_SCOPE, BackgroundKeepaliveScope.ACTIVE_TAB_ONLY.name)
                ?.let { runCatching { BackgroundKeepaliveScope.valueOf(it) }.getOrNull() }
                ?: BackgroundKeepaliveScope.ACTIVE_TAB_ONLY
        )
    )
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun setPreferFastResume(enabled: Boolean) {
        update(_settings.value.copy(preferFastResume = enabled))
    }

    fun setSshKeepaliveEnabled(enabled: Boolean) {
        update(_settings.value.copy(sshKeepaliveEnabled = enabled))
    }

    fun setBackgroundKeepaliveProfile(profile: BackgroundKeepaliveProfile) {
        update(_settings.value.copy(backgroundKeepaliveProfile = profile))
    }

    fun setBackgroundKeepaliveScope(scope: BackgroundKeepaliveScope) {
        update(_settings.value.copy(backgroundKeepaliveScope = scope))
    }

    private fun update(next: AppSettings) {
        _settings.value = next
        prefs.edit()
            .putBoolean(KEY_FAST_RESUME, next.preferFastResume)
            .putBoolean(KEY_SSH_KEEPALIVE, next.sshKeepaliveEnabled)
            .putString(KEY_BACKGROUND_KEEPALIVE_PROFILE, next.backgroundKeepaliveProfile.name)
            .putString(KEY_BACKGROUND_KEEPALIVE_SCOPE, next.backgroundKeepaliveScope.name)
            .apply()
    }

    companion object {
        private const val KEY_FAST_RESUME = "prefer_fast_resume"
        private const val KEY_SSH_KEEPALIVE = "ssh_keepalive_enabled"
        private const val KEY_BACKGROUND_KEEPALIVE_PROFILE = "background_keepalive_profile"
        private const val KEY_BACKGROUND_KEEPALIVE_SCOPE = "background_keepalive_scope"
    }
}
