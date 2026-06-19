package se.joynes.terminalhub.ui.screen.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import se.joynes.terminalhub.data.runtime.AppRuntimeRepository
import se.joynes.terminalhub.data.settings.AppSettingsRepository
import se.joynes.terminalhub.data.settings.BackgroundKeepaliveProfile
import se.joynes.terminalhub.data.settings.BackgroundKeepaliveScope
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: AppSettingsRepository,
    private val runtimeRepository: AppRuntimeRepository
) : ViewModel() {
    val settings = settingsRepository.settings
    val runtimeState = runtimeRepository.state

    fun setPreferFastResume(enabled: Boolean) {
        settingsRepository.setPreferFastResume(enabled)
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
}
