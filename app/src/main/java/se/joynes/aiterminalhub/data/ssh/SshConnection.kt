package se.joynes.aiterminalhub.data.ssh

import com.trilead.ssh2.ChannelCondition
import com.trilead.ssh2.Connection
import com.trilead.ssh2.ExtendedServerHostKeyVerifier
import com.trilead.ssh2.Session
import com.trilead.ssh2.crypto.PEMDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import se.joynes.aiterminalhub.data.logging.AppLogger
import se.joynes.aiterminalhub.data.logging.LogEvent
import se.joynes.aiterminalhub.data.logging.LogLevel
import se.joynes.aiterminalhub.data.model.Server
import java.io.IOException
import java.io.OutputStream
import javax.inject.Inject

class SshConnection @Inject constructor(
    private val logger: AppLogger
) {
    private var connection: Connection? = null
    private var shellSession: Session? = null
    private var outputStream: OutputStream? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Any()

    private val _output = MutableSharedFlow<ByteArray>(replay = 200, extraBufferCapacity = 512)
    val output: SharedFlow<ByteArray> = _output.asSharedFlow()
    private val _outputEvents = MutableStateFlow(0L)
    val outputEvents: StateFlow<Long> = _outputEvents.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    val sessionId = java.util.UUID.randomUUID().toString()

    private val permissiveHostKeyVerifier = object : ExtendedServerHostKeyVerifier() {
        override fun verifyServerHostKey(
            hostname: String?,
            port: Int,
            serverHostKeyAlgorithm: String?,
            serverHostKey: ByteArray?
        ) = true

        override fun getKnownKeyAlgorithmsForHost(host: String?, port: Int): List<String>? = null

        override fun removeServerHostKey(host: String?, port: Int, algorithm: String?, hostKey: ByteArray?) {}

        override fun addServerHostKey(hostname: String?, port: Int, algorithm: String?, hostKey: ByteArray?) {}
    }

    fun connect(server: Server, password: String?, privateKeyPem: String? = null) {
        scope.launch {
            try {
                logger.log(LogLevel.DEBUG, TAG, "Connecting to ${server.host}:${server.port}", LogEvent.SshConnect(server.host, server.port))

                val conn = Connection(server.host, server.port)
                conn.connect(permissiveHostKeyVerifier)

                val authenticated = when {
                    !privateKeyPem.isNullOrBlank() -> {
                        logger.log(LogLevel.DEBUG, TAG, "Using SSH key auth")
                        val keyPair = PEMDecoder.decode(privateKeyPem.toCharArray(), null)
                        conn.authenticateWithPublicKey(server.username, keyPair)
                    }
                    !password.isNullOrBlank() -> conn.authenticateWithPassword(server.username, password)
                    else -> conn.authenticateWithNone(server.username)
                }

                if (!authenticated) throw IOException("SSH authentication failed")

                connection = conn
                logger.log(LogLevel.INFO, TAG, "SSH session established to ${server.host}")

                val sess = conn.openSession()
                sess.requestPTY("xterm-256color", 80, 24, 0, 0, null)
                sess.startShell()
                shellSession = sess
                outputStream = sess.stdin
                _connected.value = true
                logger.log(LogLevel.INFO, TAG, "Shell channel opened")
                readOutput(sess)
            } catch (e: Exception) {
                logger.log(LogLevel.ERROR, TAG, "Connection failed: ${e.message}")
                _connected.value = false
            }
        }
    }

    /** Run a command silently via a non-PTY exec channel and wait for it to finish. */
    suspend fun runSilent(command: String) {
        val conn = connection ?: return
        withContext(Dispatchers.IO) {
            var session: Session? = null
            try {
                session = conn.openSession()
                session.execCommand("bash -lc '${command.replace("'", "'\\''")}'")
                val stdout = session.stdout
                val stderr = session.stderr
                val buf = ByteArray(1024)
                while (true) {
                    while ((stdout?.available() ?: 0) > 0) stdout?.read(buf)
                    while ((stderr?.available() ?: 0) > 0) {
                        val n = stderr?.read(buf) ?: 0
                        if (n > 0) logger.log(LogLevel.WARN, TAG, "Setup stderr: ${String(buf, 0, n)}")
                    }
                    val conditions = session.waitForCondition(
                        ChannelCondition.EOF or ChannelCondition.CLOSED or ChannelCondition.EXIT_STATUS,
                        50
                    )
                    if ((conditions and (ChannelCondition.EOF or ChannelCondition.CLOSED)) != 0) break
                    delay(50)
                }
                logger.log(LogLevel.DEBUG, TAG, "Silent exec done (exit=${session.exitStatus}): $command")
            } catch (e: Exception) {
                logger.log(LogLevel.WARN, TAG, "Silent exec failed: ${e.message}")
            } finally {
                session?.close()
            }
        }
    }

    /** Notify the server that the terminal has been resized. */
    fun resizePty(cols: Int, rows: Int) {
        if (cols <= 0 || rows <= 0) {
            logger.log(LogLevel.WARN, TAG, "PTY resize ignored: invalid ${cols}x${rows}")
            return
        }
        scope.launch {
            try {
                shellSession?.resizePTY(cols, rows, 0, 0)
                logger.log(LogLevel.DEBUG, TAG, "PTY resize: ${cols}x${rows}")
            } catch (e: Exception) {
                logger.log(LogLevel.WARN, TAG, "PTY resize failed: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    private fun readOutput(session: Session) {
        scope.launch {
            val buffer = ByteArray(4096)
            try {
                while (_connected.value) {
                    val conditions = session.waitForCondition(
                        ChannelCondition.STDOUT_DATA or ChannelCondition.STDERR_DATA or ChannelCondition.EOF,
                        0
                    )

                    if ((conditions and ChannelCondition.STDOUT_DATA) != 0) {
                        val n = session.stdout?.read(buffer) ?: 0
                        if (n > 0) {
                            logger.log(LogLevel.TRACE, TAG, "RX $n bytes", LogEvent.SshReceive(sessionId, n))
                            _output.emit(buffer.copyOf(n))
                            _outputEvents.value += 1
                        }
                    }

                    if ((conditions and ChannelCondition.STDERR_DATA) != 0) {
                        while ((session.stderr?.available() ?: 0) > 0) {
                            val n = session.stderr?.read(buffer) ?: 0
                            if (n > 0) logger.log(LogLevel.WARN, TAG, "SSH stderr: ${String(buffer, 0, n)}")
                        }
                    }

                    if ((conditions and ChannelCondition.EOF) != 0) {
                        logger.log(LogLevel.WARN, TAG, "SSH read loop: EOF, connected=${_connected.value}")
                        break
                    }
                }
            } catch (e: Exception) {
                logger.log(LogLevel.WARN, TAG, "SSH read loop exception: ${e.javaClass.simpleName}: ${e.message}\n${e.stackTraceToString()}")
            }
            val reason = if (!_connected.value) "connected=false" else "loop-exit"
            _connected.value = false
            logger.log(LogLevel.INFO, TAG, "SSH read loop ended (reason=$reason)")
        }
    }

    fun send(text: String) = sendBytes(text.toByteArray(Charsets.UTF_8))

    suspend fun awaitOutputQuiescence(
        requireNewOutput: Boolean = false,
        quietMs: Long = 400,
        timeoutMs: Long = 5_000
    ): Boolean = withTimeoutOrNull(timeoutMs) {
        var lastSeen = outputEvents.value
        if (requireNewOutput) {
            outputEvents.first { it > lastSeen || !connected.value }
            lastSeen = outputEvents.value
        }

        while (connected.value) {
            delay(quietMs)
            val now = outputEvents.value
            if (now == lastSeen) return@withTimeoutOrNull true
            lastSeen = now
        }

        false
    } ?: false

    fun sendBytes(bytes: ByteArray) {
        scope.launch {
            synchronized(writeLock) {
                try {
                    val os = outputStream ?: return@synchronized
                    os.write(bytes)
                    os.flush()
                    logger.log(LogLevel.TRACE, TAG, "TX ${bytes.size} bytes ${describeBytes(bytes)}", LogEvent.SshSend(sessionId, bytes.size))
                } catch (e: Exception) {
                    logger.log(LogLevel.WARN, TAG, "Send failed: ${e.javaClass.simpleName}: ${e.message}")
                }
            }
        }
    }

    fun disconnect() {
        _connected.value = false
        scope.launch {
            try {
                outputStream?.close()
            } catch (_: Exception) {}
            try {
                shellSession?.close()
            } catch (e: Exception) {
                logger.log(LogLevel.WARN, TAG, "Session close failed: ${e.javaClass.simpleName}: ${e.message}")
            }
            try {
                connection?.close()
            } catch (e: Exception) {
                logger.log(LogLevel.WARN, TAG, "Connection close failed: ${e.javaClass.simpleName}: ${e.message}")
            }
            shellSession = null
            connection = null
            outputStream = null
            logger.log(LogLevel.INFO, TAG, "Disconnected")
        }
    }

    private fun describeBytes(bytes: ByteArray): String {
        val shown = bytes.take(24)
        return shown.joinToString(prefix = "[", postfix = if (bytes.size > shown.size) " ...]" else "]") {
            "%02x".format(it)
        }
    }

    companion object { private const val TAG = "SshConnection" }
}
