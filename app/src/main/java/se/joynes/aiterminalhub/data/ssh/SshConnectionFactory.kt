package se.joynes.aiterminalhub.data.ssh

import se.joynes.aiterminalhub.data.logging.AppLogger
import javax.inject.Inject

class SshConnectionFactory @Inject constructor(
    private val logger: AppLogger
) {
    fun create(): SshConnection = SshConnection(logger)
}
