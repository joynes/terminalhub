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
    internal class Entry(
        val transport: CompletableDeferred<SshTransport> = CompletableDeferred(),
        val leases: MutableMap<String, Long?> = linkedMapOf()
    )

    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val entries = mutableMapOf<SshTransportKey, MutableList<Entry>>()

    suspend fun acquire(
        server: Server,
        password: String?,
        privateKeyPem: String?
    ): SshTransportLease {
        val key = transportKey(server, password, privateKeyPem)
        val leaseId = UUID.randomUUID().toString()
        val entry = synchronized(lock) {
            entries[key]
                ?.firstOrNull { it.leases.size < MAX_PROJECT_CHANNELS_PER_TRANSPORT }
                ?.also { it.leases[leaseId] = null }
                ?: Entry().also {
                    it.leases[leaseId] = null
                    entries.getOrPut(key, ::mutableListOf).add(it)
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
            if (entry in entries[key].orEmpty()) {
                transport.updateProjectIds(entry.leases.values.filterNotNull().toSet())
            }
        }
        return SshTransportLease(this, key, entry, leaseId, transport)
    }

    internal fun bindProject(lease: SshTransportLease, projectId: Long) {
        synchronized(lock) {
            if (lease.entry !in entries[lease.key].orEmpty() || lease.leaseId !in lease.entry.leases) return
            lease.entry.leases[lease.leaseId] = projectId
            lease.transport.updateProjectIds(lease.entry.leases.values.filterNotNull().toSet())
        }
    }

    internal fun release(lease: SshTransportLease) {
        var closeTransport = false
        synchronized(lock) {
            val shards = entries[lease.key] ?: return
            if (lease.entry !in shards) return
            lease.entry.leases.remove(lease.leaseId)
            closeTransport = lease.entry.leases.isEmpty()
            if (closeTransport) {
                shards.remove(lease.entry)
                if (shards.isEmpty()) entries.remove(lease.key)
            }
            lease.transport.updateProjectIds(lease.entry.leases.values.filterNotNull().toSet())
        }
        if (closeTransport) {
            lease.transport.close()
            logger.log(LogLevel.INFO, TAG, "Closed unused shared SSH transport")
        }
    }

    fun activeTransportCount(): Int = synchronized(lock) { entries.values.sumOf { it.size } }

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
                    val isOrphaned = entry !in entries[key].orEmpty() || entry.leases.isEmpty()
                    if (!isOrphaned) entry.transport.complete(transport)
                    isOrphaned
                }
                if (orphaned) {
                    transport.close()
                    entry.transport.cancel()
                }
            } catch (error: Exception) {
                synchronized(lock) {
                    entries[key]?.let { shards ->
                        shards.remove(entry)
                        if (shards.isEmpty()) entries.remove(key)
                    }
                    entry.transport.completeExceptionally(error)
                }
            }
        }
    }

    private fun removePendingLease(key: SshTransportKey, entry: Entry, leaseId: String) {
        synchronized(lock) {
            val shards = entries[key] ?: return
            if (entry !in shards) return
            entry.leases.remove(leaseId)
            if (entry.leases.isEmpty()) {
                shards.remove(entry)
                if (shards.isEmpty()) entries.remove(key)
            }
        }
    }

    internal companion object {
        private const val TAG = "SharedSshTransportPool"
        internal const val MAX_PROJECT_CHANNELS_PER_TRANSPORT = 8

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

    fun openSession() = transport.openProjectSession()
    fun openAuxiliarySession() = transport.openAuxiliarySession()
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
