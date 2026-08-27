package se.joynes.terminalhub.data.ssh

import se.joynes.terminalhub.data.logging.AppLogger
import se.joynes.terminalhub.data.runtime.AppRuntimeRepository
import se.joynes.terminalhub.data.settings.AppSettingsRepository
import javax.inject.Inject

class SshConnectionFactory @Inject constructor(
    private val logger: AppLogger,
    private val settingsRepository: AppSettingsRepository,
    private val runtimeRepository: AppRuntimeRepository,
    private val hostKeyVerifier: TerminalHubHostKeyVerifier
) {
    fun create(): SshConnection = SshConnection(logger, settingsRepository, runtimeRepository, hostKeyVerifier)
}
