package se.joynes.terminalhub.data.ssh

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import se.joynes.terminalhub.data.model.Server
import javax.inject.Inject
import javax.inject.Singleton

data class SshTunnel(val serverId: Long, val serverName: String, val localPort: Int, val remotePort: Int) {
    val url: String get() = "http://127.0.0.1:$localPort/"
}

/** Explicit, app-lifetime tunnels. Closing a tunnel releases only its lease, never terminal tabs. */
@Singleton
class SshTunnelManager @Inject constructor(private val pool: SharedSshTransportPool) {
    private data class Open(val tunnel: SshTunnel, val forward: AutoCloseable, val lease: SshTransportLease)
    private val mutex = Mutex()
    private val open = mutableMapOf<Int, Open>()
    private val _tunnels = MutableStateFlow<List<SshTunnel>>(emptyList())
    val tunnels = _tunnels.asStateFlow()

    suspend fun start(server: Server, password: String?, key: String?, localPort: Int, remotePort: Int) =
        withContext(Dispatchers.IO) {
            require(localPort in 1024..65535) { "Phone port must be between 1024 and 65535" }
            require(remotePort in 1..65535) { "Server port must be between 1 and 65535" }
            mutex.withLock {
                check(localPort !in open) { "Phone port is already used by a tunnel" }
                val lease = pool.acquireTunnel(server, password, key)
                try {
                    val forward = lease.openLocalForward(localPort, remotePort)
                    open[localPort] = Open(SshTunnel(server.id, server.name, localPort, remotePort), forward, lease)
                    _tunnels.value = open.values.map { it.tunnel }
                } catch (error: Exception) {
                    lease.release()
                    throw error
                }
            }
        }

    suspend fun stop(localPort: Int) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val item = open.remove(localPort) ?: return@withLock
            try { item.forward.close() } finally {
                item.lease.release()
                _tunnels.value = open.values.map { it.tunnel }
            }
        }
    }
}
