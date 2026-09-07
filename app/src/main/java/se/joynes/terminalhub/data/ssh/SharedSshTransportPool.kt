package se.joynes.terminalhub.data.ssh

import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import se.joynes.terminalhub.data.logging.AppLogger
import se.joynes.terminalhub.data.logging.LogLevel
import se.joynes.terminalhub.data.model.Server
import javax.inject.Inject
import javax.inject.Singleton

internal data class SshTransportKey(
    val host: String,
    val port: Int,
    val username: String,
    val authenticationFingerprint: String
)

/**
 * Deduplicates authenticated transports. Every lease still opens its own Session/PTY channel,
 * so terminal input, output, resize events and tmux attachment remain isolated per project.
 */
@Singleton
class SharedSshTransportPool @Inject constructor(
    private val logger: AppLogger,
    private val connector: SshTransportConnector
) {
    internal data class Entry(
        val transport: CompletableDeferred<SshTransport> = CompletableDeferred(),
        val leases: MutableMap<String, Long?> = linkedMapOf()
    )

    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val entries = mutableMapOf<SshTransportKey, Entry>()

    suspend fun acquire(
        server: Server,
        password: String?,
        privateKeyPem: String?
    ): SshTransportLease {
        val key = transportKey(server, password, privateKeyPem)
        val leaseId = UUID.randomUUID().toString()
        val entry = synchronized(lock) {
            entries[key]?.also { it.leases[leaseId] = null } ?: Entry().also {
                it.leases[leaseId] = null
                entries[key] = it
                startConnection(key, it, server, password, privateKeyPem)
            }
        }

        val transport = try {
            entry.transport.await()
        } catch (cancelled: CancellationException) {
            removePendingLease(key, entry, leaseId)
            throw cancelled
        } catch (error: Exception) {
            throw error
        }
        synchronized(lock) {
            if (entries[key] === entry) {
                transport.updateProjectIds(entry.leases.values.filterNotNull().toSet())
            }
        }
        return SshTransportLease(this, key, entry, leaseId, transport)
    }

    internal fun bindProject(lease: SshTransportLease, projectId: Long) {
        synchronized(lock) {
            val entry = entries[lease.key]
            if (entry !== lease.entry || lease.leaseId !in entry.leases) return
            entry.leases[lease.leaseId] = projectId
            lease.transport.updateProjectIds(entry.leases.values.filterNotNull().toSet())
        }
    }

    internal fun release(lease: SshTransportLease) {
        var closeTransport = false
        synchronized(lock) {
            val entry = entries[lease.key]
            if (entry !== lease.entry) return
            entry.leases.remove(lease.leaseId)
            closeTransport = entry.leases.isEmpty()
            if (closeTransport) entries.remove(lease.key)
            lease.transport.updateProjectIds(entry.leases.values.filterNotNull().toSet())
        }
        if (closeTransport) {
            lease.transport.close()
            logger.log(LogLevel.INFO, TAG, "Closed unused shared SSH transport")
        }
    }

    fun activeTransportCount(): Int = synchronized(lock) { entries.size }

    private fun startConnection(
        key: SshTransportKey,
        entry: Entry,
        server: Server,
        password: String?,
        privateKeyPem: String?
    ) {
        logger.log(LogLevel.INFO, TAG, "Opening shared SSH transport for ${server.username}@${server.host}:${server.port}")
        scope.launch {
            try {
                val transport = connector.connect(server, password, privateKeyPem)
                val orphaned = synchronized(lock) {
                    val isOrphaned = entries[key] !== entry || entry.leases.isEmpty()
                    if (!isOrphaned) entry.transport.complete(transport)
                    isOrphaned
                }
                if (orphaned) {
                    transport.close()
                    entry.transport.cancel()
                }
            } catch (error: Exception) {
                synchronized(lock) {
                    if (entries[key] === entry) entries.remove(key)
                    entry.transport.completeExceptionally(error)
                }
            }
        }
    }

    private fun removePendingLease(key: SshTransportKey, entry: Entry, leaseId: String) {
        synchronized(lock) {
            if (entries[key] !== entry) return
            entry.leases.remove(leaseId)
            if (entry.leases.isEmpty()) entries.remove(key)
        }
    }

    internal companion object {
        private const val TAG = "SharedSshTransportPool"

        fun transportKey(server: Server, password: String?, privateKeyPem: String?): SshTransportKey {
            val authMaterial = when {
                !privateKeyPem.isNullOrBlank() -> "key:$privateKeyPem"
                !password.isNullOrBlank() -> "password:$password"
                else -> "none"
            }
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(authMaterial.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            return SshTransportKey(
                host = server.host.trim().trimEnd('.').lowercase(),
                port = server.port,
                username = server.username,
                authenticationFingerprint = digest
            )
        }
    }
}

class SshTransportLease internal constructor(
    private val pool: SharedSshTransportPool,
    internal val key: SshTransportKey,
    internal val entry: SharedSshTransportPool.Entry,
    internal val leaseId: String,
    internal val transport: SshTransport
) {
    @Volatile private var released = false

    fun openSession() = transport.openSession()
    fun bindProject(projectId: Long) = pool.bindProject(this, projectId)

    fun release() {
        if (released) return
        synchronized(this) {
            if (released) return
            released = true
        }
        pool.release(this)
    }

    fun debugTransportIdentity(): Int = transport.debugIdentity()
}
