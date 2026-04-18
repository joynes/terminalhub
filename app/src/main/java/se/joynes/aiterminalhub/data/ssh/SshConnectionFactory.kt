package se.joynes.aiterminalhub.data.ssh

import se.joynes.aiterminalhub.data.logging.AppLogger
import se.joynes.aiterminalhub.data.settings.AppSettingsRepository
import javax.inject.Inject

class SshConnectionFactory @Inject constructor(
    private val logger: AppLogger,
    private val settingsRepository: AppSettingsRepository
) {
    fun create(): SshConnection = SshConnection(logger, settingsRepository)
}
