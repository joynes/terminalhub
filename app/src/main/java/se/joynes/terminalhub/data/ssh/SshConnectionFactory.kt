package se.joynes.terminalhub.data.ssh

import se.joynes.terminalhub.data.logging.AppLogger
import se.joynes.terminalhub.data.runtime.AppRuntimeRepository
import se.joynes.terminalhub.data.settings.AppSettingsRepository
import javax.inject.Inject

class SshConnectionFactory @Inject constructor(
    private val logger: AppLogger,
    private val transportPool: SharedSshTransportPool
) {
    fun create(): SshConnection = SshConnection(logger, transportPool)
}
