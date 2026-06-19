package se.joynes.aiterminal.data.ssh

import se.joynes.aiterminal.data.logging.AppLogger
import se.joynes.aiterminal.data.runtime.AppRuntimeRepository
import se.joynes.aiterminal.data.settings.AppSettingsRepository
import javax.inject.Inject

class SshConnectionFactory @Inject constructor(
    private val logger: AppLogger,
    private val settingsRepository: AppSettingsRepository,
    private val runtimeRepository: AppRuntimeRepository
) {
    fun create(): SshConnection = SshConnection(logger, settingsRepository, runtimeRepository)
}
