package se.joynes.terminalhub.data.ssh

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import se.joynes.terminalhub.data.logging.AppLogger
import se.joynes.terminalhub.data.logging.LogLevel
import se.joynes.terminalhub.data.model.Server
import se.joynes.terminalhub.data.model.SshSession
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SshManager @Inject constructor(
    private val logger: AppLogger,
    private val connectionFactory: SshConnectionFactory
) {
    private val _sessions = MutableStateFlow<Map<String, SshConnection>>(emptyMap())
    val sessions: StateFlow<Map<String, SshConnection>> = _sessions.asStateFlow()

    fun createSession(server: Server, password: String?, privateKeyPem: String? = null): SshConnection {
        val conn = connectionFactory.create()
        conn.connect(server, password, privateKeyPem)
        _sessions.value = _sessions.value + (conn.sessionId to conn)
        logger.log(LogLevel.INFO, TAG, "Session created: ${conn.sessionId} for ${server.host}; snapshot=${debugSnapshot()}")
        return conn
    }

    fun destroySession(sessionId: String) {
        _sessions.value[sessionId]?.disconnect()
        _sessions.value = _sessions.value - sessionId
        logger.log(LogLevel.INFO, TAG, "Session destroyed: $sessionId; snapshot=${debugSnapshot()}")
    }

    fun getSession(sessionId: String): SshConnection? = _sessions.value[sessionId]

    fun debugSnapshot(): String = buildString {
        append("count=").append(_sessions.value.size)
        append(",ids=")
        append(_sessions.value.keys.joinToString(prefix = "[", postfix = "]"))
    }

    companion object { private const val TAG = "SshManager" }
}
