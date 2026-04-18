package se.joynes.aiterminalhub.ui.screen.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import se.joynes.aiterminalhub.data.settings.AppSettingsRepository
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: AppSettingsRepository
) : ViewModel() {
    val settings = settingsRepository.settings

    fun setPreferFastResume(enabled: Boolean) {
        settingsRepository.setPreferFastResume(enabled)
    }

    fun setSshKeepaliveEnabled(enabled: Boolean) {
        settingsRepository.setSshKeepaliveEnabled(enabled)
    }
}
