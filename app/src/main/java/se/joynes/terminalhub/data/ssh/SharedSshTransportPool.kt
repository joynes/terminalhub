package se.joynes.terminalhub.data.ssh

import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
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
        val projectLeases: MutableMap<String, Long?> = linkedMapOf(),
        val transferLeases: MutableSet<String> = linkedSetOf()
    )

    internal enum class LeaseKind { PROJECT, TRANSFER }

    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val transferSlots = Semaphore(MAX_CONCURRENT_TRANSFERS)
    private val entries = mutableMapOf<SshTransportKey, MutableList<Entry>>()

    suspend fun acquire(
        server: Server,
        password: String?,
        privateKeyPem: String?
    ): SshTransportLease = acquireLease(server, password, privateKeyPem, LeaseKind.PROJECT)

    suspend fun acquireTransfer(
        server: Server,
        password: String?,
        privateKeyPem: String?
    ): SshTransportLease {
        transferSlots.acquire()
        return try {
            acquireLease(server, password, privateKeyPem, LeaseKind.TRANSFER)
        } catch (error: Exception) {
            transferSlots.release()
            throw error
        }
    }

    private suspend fun acquireLease(
        server: Server,
        password: String?,
        privateKeyPem: String?,
        kind: LeaseKind
    ): SshTransportLease {
        val key = transportKey(server, password, privateKeyPem)
        val leaseId = UUID.randomUUID().toString()
        val entry = synchronized(lock) {
            entries[key]
                ?.filter { candidate ->
                    when (kind) {
                        LeaseKind.PROJECT -> candidate.projectLeases.size < MAX_PROJECT_CHANNELS_PER_TRANSPORT
                        LeaseKind.TRANSFER -> candidate.transferLeases.size < MAX_TRANSFERS_PER_TRANSPORT
                    }
                }
                ?.minByOrNull { it.transferLeases.size }
                ?.also { it.addLease(leaseId, kind) }
                ?: Entry().also {
                    it.addLease(leaseId, kind)
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
                transport.updateProjectIds(entry.projectLeases.values.filterNotNull().toSet())
            }
        }
        return SshTransportLease(this, key, entry, leaseId, transport, kind)
    }

    internal fun bindProject(lease: SshTransportLease, projectId: Long) {
        synchronized(lock) {
            if (lease.kind != LeaseKind.PROJECT) return
            if (lease.entry !in entries[lease.key].orEmpty() || lease.leaseId !in lease.entry.projectLeases) return
            lease.entry.projectLeases[lease.leaseId] = projectId
            lease.transport.updateProjectIds(lease.entry.projectLeases.values.filterNotNull().toSet())
        }
    }

    internal fun release(lease: SshTransportLease) {
        try {
            var closeTransport = false
            synchronized(lock) {
                val shards = entries[lease.key] ?: return
                if (lease.entry !in shards) return
                lease.entry.removeLease(lease.leaseId, lease.kind)
                closeTransport = lease.entry.isUnused()
                if (closeTransport) {
                    shards.remove(lease.entry)
                    if (shards.isEmpty()) entries.remove(lease.key)
                }
                lease.transport.updateProjectIds(lease.entry.projectLeases.values.filterNotNull().toSet())
            }
            if (closeTransport) {
                lease.transport.close()
                logger.log(LogLevel.INFO, TAG, "Closed unused shared SSH transport")
            }
        } finally {
            if (lease.kind == LeaseKind.TRANSFER) transferSlots.release()
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
                    val isOrphaned = entry !in entries[key].orEmpty() || entry.isUnused()
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
            entry.projectLeases.remove(leaseId)
            entry.transferLeases.remove(leaseId)
            if (entry.isUnused()) {
                shards.remove(entry)
                if (shards.isEmpty()) entries.remove(key)
            }
        }
    }

    internal companion object {
        private const val TAG = "SharedSshTransportPool"
        internal const val MAX_PROJECT_CHANNELS_PER_TRANSPORT = 8
        internal const val MAX_TRANSFERS_PER_TRANSPORT = 2
        internal const val MAX_CONCURRENT_TRANSFERS = 4

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

    private fun Entry.addLease(leaseId: String, kind: LeaseKind) {
        when (kind) {
            LeaseKind.PROJECT -> projectLeases[leaseId] = null
            LeaseKind.TRANSFER -> transferLeases += leaseId
        }
    }

    private fun Entry.removeLease(leaseId: String, kind: LeaseKind) {
        when (kind) {
            LeaseKind.PROJECT -> projectLeases.remove(leaseId)
            LeaseKind.TRANSFER -> transferLeases.remove(leaseId)
        }
    }

    private fun Entry.isUnused(): Boolean = projectLeases.isEmpty() && transferLeases.isEmpty()
}

class SshTransportLease internal constructor(
    private val pool: SharedSshTransportPool,
    internal val key: SshTransportKey,
    internal val entry: SharedSshTransportPool.Entry,
    internal val leaseId: String,
    internal val transport: SshTransport,
    internal val kind: SharedSshTransportPool.LeaseKind
) {
    @Volatile private var released = false

    fun openSession() = transport.openProjectSession()
    fun openAuxiliarySession() = transport.openAuxiliarySession()
    fun openSftpSession() = transport.openSftpSession()
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
