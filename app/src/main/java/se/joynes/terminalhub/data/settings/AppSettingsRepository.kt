package se.joynes.terminalhub.data.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class AppSettings(
    val preferFastResume: Boolean = true,
    val executeTextInputOnSend: Boolean = false,
    val sshKeepaliveEnabled: Boolean = true,
    val keepSshActiveInBackground: Boolean = false,
    val backgroundSshRecommendationHandled: Boolean = false,
    val backgroundKeepaliveProfile: BackgroundKeepaliveProfile = BackgroundKeepaliveProfile.BALANCED,
    val backgroundKeepaliveScope: BackgroundKeepaliveScope = BackgroundKeepaliveScope.ACTIVE_TAB_ONLY,
    val keyBarRows: List<List<String>> = KeyBarLayoutConfig.defaultRows
)

enum class BackgroundKeepaliveProfile {
    AGGRESSIVE,
    BALANCED,
    BATTERY_SAVER,
    ULTRA_BATTERY_SAVER
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
            executeTextInputOnSend = prefs.getBoolean(KEY_EXECUTE_TEXT_INPUT_ON_SEND, false),
            sshKeepaliveEnabled = prefs.getBoolean(KEY_SSH_KEEPALIVE, true),
            keepSshActiveInBackground = prefs.getBoolean(KEY_KEEP_SSH_ACTIVE_IN_BACKGROUND, false),
            backgroundSshRecommendationHandled = prefs.getBoolean(KEY_BACKGROUND_SSH_RECOMMENDATION_HANDLED, false),
            backgroundKeepaliveProfile = prefs.getString(KEY_BACKGROUND_KEEPALIVE_PROFILE, BackgroundKeepaliveProfile.BALANCED.name)
                ?.let { runCatching { BackgroundKeepaliveProfile.valueOf(it) }.getOrNull() }
                ?: BackgroundKeepaliveProfile.BALANCED,
            backgroundKeepaliveScope = prefs.getString(KEY_BACKGROUND_KEEPALIVE_SCOPE, BackgroundKeepaliveScope.ACTIVE_TAB_ONLY.name)
                ?.let { runCatching { BackgroundKeepaliveScope.valueOf(it) }.getOrNull() }
                ?: BackgroundKeepaliveScope.ACTIVE_TAB_ONLY,
            keyBarRows = KeyBarLayoutConfig.decode(prefs.getString(KEY_KEY_BAR_LAYOUT, null))
        )
    )
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun setPreferFastResume(enabled: Boolean) {
        update(_settings.value.copy(preferFastResume = enabled))
    }

    fun setExecuteTextInputOnSend(enabled: Boolean) {
        update(_settings.value.copy(executeTextInputOnSend = enabled))
    }

    fun setSshKeepaliveEnabled(enabled: Boolean) {
        update(_settings.value.copy(sshKeepaliveEnabled = enabled))
    }

    fun setKeepSshActiveInBackground(enabled: Boolean) {
        update(_settings.value.copy(keepSshActiveInBackground = enabled))
    }

    fun setBackgroundSshRecommendationHandled(handled: Boolean = true) {
        update(_settings.value.copy(backgroundSshRecommendationHandled = handled))
    }

    /** Active background mode never survives a process restart or starts itself again. */
    fun resetBackgroundSshModeForProcessStart() {
        if (_settings.value.keepSshActiveInBackground) {
            setKeepSshActiveInBackground(false)
        }
    }

    fun setBackgroundKeepaliveProfile(profile: BackgroundKeepaliveProfile) {
        update(_settings.value.copy(backgroundKeepaliveProfile = profile))
    }

    fun setBackgroundKeepaliveScope(scope: BackgroundKeepaliveScope) {
        update(_settings.value.copy(backgroundKeepaliveScope = scope))
    }

    fun setKeyBarRows(rows: List<List<String>>) {
        update(_settings.value.copy(keyBarRows = KeyBarLayoutConfig.normalize(rows)))
    }

    private fun update(next: AppSettings) {
        _settings.value = next
        prefs.edit()
            .putBoolean(KEY_FAST_RESUME, next.preferFastResume)
            .putBoolean(KEY_EXECUTE_TEXT_INPUT_ON_SEND, next.executeTextInputOnSend)
            .putBoolean(KEY_SSH_KEEPALIVE, next.sshKeepaliveEnabled)
            .putBoolean(KEY_KEEP_SSH_ACTIVE_IN_BACKGROUND, next.keepSshActiveInBackground)
            .putBoolean(KEY_BACKGROUND_SSH_RECOMMENDATION_HANDLED, next.backgroundSshRecommendationHandled)
            .putString(KEY_BACKGROUND_KEEPALIVE_PROFILE, next.backgroundKeepaliveProfile.name)
            .putString(KEY_BACKGROUND_KEEPALIVE_SCOPE, next.backgroundKeepaliveScope.name)
            .putString(KEY_KEY_BAR_LAYOUT, KeyBarLayoutConfig.encode(next.keyBarRows))
            .apply()
    }

    companion object {
        private const val KEY_FAST_RESUME = "prefer_fast_resume"
        private const val KEY_EXECUTE_TEXT_INPUT_ON_SEND = "execute_text_input_on_send"
        private const val KEY_SSH_KEEPALIVE = "ssh_keepalive_enabled"
        private const val KEY_KEEP_SSH_ACTIVE_IN_BACKGROUND = "keep_ssh_active_in_background"
        private const val KEY_BACKGROUND_SSH_RECOMMENDATION_HANDLED = "background_ssh_recommendation_handled"
        private const val KEY_BACKGROUND_KEEPALIVE_PROFILE = "background_keepalive_profile"
        private const val KEY_BACKGROUND_KEEPALIVE_SCOPE = "background_keepalive_scope"
        private const val KEY_KEY_BAR_LAYOUT = "key_bar_layout"
    }
}
