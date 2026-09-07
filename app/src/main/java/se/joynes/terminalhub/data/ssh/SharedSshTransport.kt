package se.joynes.terminalhub.data.ssh

import com.trilead.ssh2.Connection
import com.trilead.ssh2.Session
import com.trilead.ssh2.crypto.PEMDecoder
import java.io.IOException
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import se.joynes.terminalhub.data.logging.AppLogger
import se.joynes.terminalhub.data.logging.LogLevel
import se.joynes.terminalhub.data.model.Server
import se.joynes.terminalhub.data.runtime.AppRuntimeRepository
import se.joynes.terminalhub.data.settings.AppSettingsRepository
import se.joynes.terminalhub.data.settings.BackgroundKeepaliveProfile
import se.joynes.terminalhub.data.settings.BackgroundKeepaliveScope
import javax.inject.Inject

/** One authenticated SSH transport which can multiplex several project channels. */
interface SshTransport {
    fun openProjectSession(): Session
    fun openAuxiliarySession(): SshAuxiliarySession
    fun updateProjectIds(projectIds: Set<Long>)
    fun close()
    fun debugIdentity(): Int
}

/** A short-lived channel which returns its reserved slot when closed. */
class SshAuxiliarySession internal constructor(
    val session: Session,
    private val releasePermit: () -> Unit
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        try {
            session.close()
        } finally {
            releasePermit()
        }
    }
}

interface SshTransportConnector {
    fun connect(server: Server, password: String?, privateKeyPem: String?): SshTransport
}

class TrileadSshTransportConnector @Inject constructor(
    private val logger: AppLogger,
    private val settingsRepository: AppSettingsRepository,
    private val runtimeRepository: AppRuntimeRepository,
    private val hostKeyVerifier: TerminalHubHostKeyVerifier
) : SshTransportConnector {
    override fun connect(server: Server, password: String?, privateKeyPem: String?): SshTransport {
        val connection = Connection(server.host, server.port)
        try {
            withVerifiedHostKey(hostKeyVerifier, server.host, server.port) {
                connection.connect(hostKeyVerifier, CONNECT_TIMEOUT_MS, CONNECT_TIMEOUT_MS)
            }

            val authenticated = when {
                !privateKeyPem.isNullOrBlank() -> {
                    val keyPair = PEMDecoder.decode(privateKeyPem.toCharArray(), null)
                    connection.authenticateWithPublicKey(server.username, keyPair)
                }
                !password.isNullOrBlank() -> connection.authenticateWithPassword(server.username, password)
                else -> connection.authenticateWithNone(server.username)
            }
            if (!authenticated) throw IOException("SSH authentication failed")

            return TrileadSshTransport(connection, logger, settingsRepository, runtimeRepository)
        } catch (error: Exception) {
            connection.close()
            throw error
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
    }
}

private class TrileadSshTransport(
    private val connection: Connection,
    private val logger: AppLogger,
    private val settingsRepository: AppSettingsRepository,
    private val runtimeRepository: AppRuntimeRepository
) : SshTransport {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val projectIds = AtomicReference<Set<Long>>(emptySet())
    private val auxiliaryChannelPermits = Semaphore(AUXILIARY_CHANNEL_CAPACITY, true)
    private val keepaliveJob: Job = scope.launch { keepaliveLoop() }

    override fun openProjectSession(): Session = connection.openSession()

    override fun openAuxiliarySession(): SshAuxiliarySession {
        auxiliaryChannelPermits.acquire()
        return try {
            SshAuxiliarySession(connection.openSession(), auxiliaryChannelPermits::release)
        } catch (error: Exception) {
            auxiliaryChannelPermits.release()
            throw error
        }
    }

    override fun updateProjectIds(projectIds: Set<Long>) {
        this.projectIds.set(projectIds)
    }

    override fun close() {
        keepaliveJob.cancel()
        connection.close()
    }

    override fun debugIdentity(): Int = System.identityHashCode(connection)

    private suspend fun keepaliveLoop() {
        while (scope.isActive) {
            delay(nextKeepaliveDelayMs())
            val settings = settingsRepository.settings.value
            if (!settings.sshKeepaliveEnabled) continue
            if (!shouldSendKeepalive(settings.backgroundKeepaliveScope)) continue
            try {
                connection.sendIgnorePacket()
            } catch (error: Exception) {
                logger.log(
                    LogLevel.WARN,
                    TAG,
                    "Shared SSH keepalive failed: ${error.javaClass.simpleName}: ${error.message}"
                )
            }
        }
    }

    private fun nextKeepaliveDelayMs(): Long {
        if (runtimeRepository.state.value.appInForeground) return FOREGROUND_KEEPALIVE_MS
        return when (settingsRepository.settings.value.backgroundKeepaliveProfile) {
            BackgroundKeepaliveProfile.AGGRESSIVE -> 30_000L
            BackgroundKeepaliveProfile.BALANCED -> 120_000L
            BackgroundKeepaliveProfile.BATTERY_SAVER -> 300_000L
            BackgroundKeepaliveProfile.ULTRA_BATTERY_SAVER -> 600_000L
        }
    }

    private fun shouldSendKeepalive(scope: BackgroundKeepaliveScope): Boolean {
        val runtimeState = runtimeRepository.state.value
        if (runtimeState.appInForeground) return true
        return when (scope) {
            BackgroundKeepaliveScope.ALL_SESSIONS -> projectIds.get().isNotEmpty()
            BackgroundKeepaliveScope.ACTIVE_TAB_ONLY -> runtimeState.activeProjectId in projectIds.get()
        }
    }

    private companion object {
        const val TAG = "SharedSshTransport"
        const val FOREGROUND_KEEPALIVE_MS = 60_000L
        const val AUXILIARY_CHANNEL_CAPACITY = 2
    }
}
