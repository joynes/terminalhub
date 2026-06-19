package se.joynes.aiterminal.ui.screen.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import se.joynes.aiterminal.data.runtime.AppRuntimeRepository
import se.joynes.aiterminal.data.settings.AppSettingsRepository
import se.joynes.aiterminal.data.settings.BackgroundKeepaliveProfile
import se.joynes.aiterminal.data.settings.BackgroundKeepaliveScope
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
