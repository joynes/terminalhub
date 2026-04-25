package se.joynes.aiterminalhub.data.ssh

import com.trilead.ssh2.ChannelCondition
import com.trilead.ssh2.Connection
import com.trilead.ssh2.ExtendedServerHostKeyVerifier
import com.trilead.ssh2.Session
import com.trilead.ssh2.crypto.PEMDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.InputStream
import se.joynes.aiterminalhub.data.logging.AppLogger
import se.joynes.aiterminalhub.data.logging.LogEvent
import se.joynes.aiterminalhub.data.logging.LogLevel
import se.joynes.aiterminalhub.data.model.Server
import se.joynes.aiterminalhub.data.runtime.AppRuntimeRepository
import se.joynes.aiterminalhub.data.settings.AppSettingsRepository
import se.joynes.aiterminalhub.data.settings.BackgroundKeepaliveProfile
import se.joynes.aiterminalhub.data.settings.BackgroundKeepaliveScope
import java.io.IOException
import java.io.OutputStream
import javax.inject.Inject

class SshConnection @Inject constructor(
    private val logger: AppLogger,
    private val settingsRepository: AppSettingsRepository,
    private val runtimeRepository: AppRuntimeRepository
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
    private val instanceId = System.identityHashCode(this)
    private var serverLabel: String = "unknown"
    private var connectAttempt = 0
    private var keepaliveJob: Job? = null
    @Volatile private var projectId: Long? = null
    @Volatile private var projectName: String? = null
    @Volatile private var createdAtMs = System.currentTimeMillis()
    @Volatile private var connectedAtMs: Long? = null
    @Volatile private var lastRxAtMs: Long? = null
    @Volatile private var lastTxAtMs: Long? = null
    @Volatile private var lastResizeAtMs: Long? = null
    @Volatile private var disconnectReason: String? = null

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

    fun bindProject(projectId: Long, projectName: String) {
        this.projectId = projectId
        this.projectName = projectName
    }

    fun connect(server: Server, password: String?, privateKeyPem: String? = null) {
        scope.launch {
            try {
                connectAttempt += 1
                serverLabel = "${server.username}@${server.host}:${server.port}"
                logger.log(
                    LogLevel.INFO,
                    TAG,
                    "Connecting to $serverLabel",
                    LogEvent.SshConnect(server.host, server.port)
                )

                val conn = Connection(server.host, server.port)
                conn.connect(permissiveHostKeyVerifier)

                val authenticated = when {
                    !privateKeyPem.isNullOrBlank() -> {
                        val keyPair = PEMDecoder.decode(privateKeyPem.toCharArray(), null)
                        conn.authenticateWithPublicKey(server.username, keyPair)
                    }
                    !password.isNullOrBlank() -> conn.authenticateWithPassword(server.username, password)
                    else -> conn.authenticateWithNone(server.username)
                }

                if (!authenticated) throw IOException("SSH authentication failed")

                connection = conn
                connectedAtMs = System.currentTimeMillis()

                val sess = conn.openSession()
                sess.requestPTY("xterm-256color", 80, 24, 0, 0, null)
                sess.startShell()
                shellSession = sess
                outputStream = sess.stdin
                disconnectReason = null
                _connected.value = true
                logger.log(LogLevel.INFO, TAG, "Connected to $serverLabel")
                startKeepaliveLoop(conn)
                readOutput(sess)
            } catch (e: Exception) {
                logger.log(LogLevel.ERROR, TAG, "Connection failed to $serverLabel: ${e.message}")
                disconnectReason = "connect-failed:${e.javaClass.simpleName}"
                _connected.value = false
            }
        }
    }

    /** Run a command silently via a non-PTY exec channel and wait for it to finish. */
    suspend fun runSilent(command: String): String {
        val conn = connection ?: return ""
        return withContext(Dispatchers.IO) {
            var session: Session? = null
            val stdoutText = StringBuilder()
            try {
                session = conn.openSession()
                session.execCommand("bash -lc '${command.replace("'", "'\\''")}'")
                val stdout = session.stdout
                val stderr = session.stderr
                val buf = ByteArray(1024)
                fun drainStreams() {
                    while ((stdout?.available() ?: 0) > 0) {
                        val n = stdout?.read(buf) ?: 0
                        if (n > 0) stdoutText.append(String(buf, 0, n))
                    }
                    while ((stderr?.available() ?: 0) > 0) {
                        val n = stderr?.read(buf) ?: 0
                        if (n > 0) logger.log(LogLevel.WARN, TAG, "Setup stderr: ${String(buf, 0, n)}")
                    }
                }
                while (true) {
                    drainStreams()
                    val conditions = session.waitForCondition(
                        ChannelCondition.EOF or ChannelCondition.CLOSED or ChannelCondition.EXIT_STATUS,
                        50
                    )
                    if ((conditions and ChannelCondition.EXIT_STATUS) != 0) break
                    if ((conditions and (ChannelCondition.EOF or ChannelCondition.CLOSED)) != 0) {
                        repeat(10) {
                            if (session.exitStatus != null) return@repeat
                            session.waitForCondition(ChannelCondition.EXIT_STATUS, 50)
                            delay(50)
                        }
                        break
                    }
                    delay(50)
                }
                drainStreams()
                val exitStatus = session.exitStatus
                when {
                    exitStatus == null -> logger.log(LogLevel.DEBUG, TAG, "Silent exec closed without exit status: $command")
                    exitStatus != 0 -> logger.log(LogLevel.WARN, TAG, "Silent exec exit $exitStatus: $command")
                }
            } catch (e: Exception) {
                logger.log(LogLevel.WARN, TAG, "Silent exec failed: ${e.message}")
            } finally {
                session?.close()
            }
            stdoutText.toString()
        }
    }

    /** Notify the server that the terminal has been resized. */
    fun resizePty(cols: Int, rows: Int) {
        if (cols <= 0 || rows <= 0) return
        scope.launch {
            try {
                val sess = shellSession ?: return@launch
                lastResizeAtMs = System.currentTimeMillis()
                sess.resizePTY(cols, rows, 0, 0)
            } catch (_: Exception) {
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
                            lastRxAtMs = System.currentTimeMillis()
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
                        val exitStatus = try { session.exitStatus } catch (_: Exception) { null }
                        disconnectReason = "read-loop-eof:${describeConditions(conditions)}:exit=$exitStatus"
                        logger.log(LogLevel.WARN, TAG, "SSH disconnected: exitStatus=$exitStatus")
                        break
                    }
                }
            } catch (e: Exception) {
                disconnectReason = "read-loop-exception:${e.javaClass.simpleName}"
                logger.log(LogLevel.WARN, TAG, "SSH read loop error: ${e.javaClass.simpleName}: ${e.message}")
            }
            val reason = if (!_connected.value) "connected=false" else "loop-exit"
            if (disconnectReason == null) disconnectReason = "read-loop-ended:$reason"
            _connected.value = false
            val exitStatus = try { session.exitStatus } catch (_: Exception) { null }
        }
    }

    /**
     * Upload a file via SCP (raw protocol over an exec channel), emitting progress after each chunk.
     * Uses the existing SSH connection — no second auth needed.
     */
    fun scpUpload(
        fileName: String,
        fileSize: Long,
        inputStream: InputStream,
        remoteDir: String
    ): Flow<ScpUploadProgress> = channelFlow {
        withContext(Dispatchers.IO) {
            val conn = connection ?: error("SSH not connected")
            var sess: Session? = null
            try {
                sess = conn.openSession()
                // Wrap in bash -lc so that ~ is expanded (raw execCommand has no shell)
                val sanitized = remoteDir.replace("'", "'\\''")
                sess.execCommand("bash -lc 'scp -t \"$sanitized\"'")
                val toRemote   = sess.stdin   // we write SCP frames here
                val fromRemote = sess.stdout  // we read acknowledgements here

                fun readAck() {
                    val code = fromRemote.read()
                    if (code != 0) {
                        val msg = StringBuilder()
                        var c: Int
                        while (fromRemote.read().also { c = it } != '\n'.code && c != -1) msg.append(c.toChar())
                        throw IOException("SCP remote error ($code): $msg")
                    }
                }

                // Send file header: "C0644 <size> <filename>\n", wait for ack
                val header = "C0644 $fileSize $fileName\n"
                toRemote.write(header.toByteArray(Charsets.UTF_8))
                toRemote.flush()
                readAck()

                // Stream file bytes in 8 KiB chunks, emit progress after each
                val buf = ByteArray(8192)
                var sent = 0L
                var n: Int
                while (inputStream.read(buf).also { n = it } != -1) {
                    toRemote.write(buf, 0, n)
                    sent += n
                    trySend(ScpUploadProgress(fileName, sent, fileSize))
                }
                toRemote.flush()

                // Send null-byte end-of-file marker, wait for ack
                toRemote.write(0)
                toRemote.flush()
                readAck()

                trySend(ScpUploadProgress(fileName, fileSize, fileSize))
            } finally {
                inputStream.close()
                sess?.close()
            }
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

    suspend fun awaitTransportQuiescence(
        quietMs: Long = 600,
        timeoutMs: Long = 6_000
    ): Boolean = withTimeoutOrNull(timeoutMs) {
        var lastOutputEvent = outputEvents.value
        var lastTx = lastTxAtMs

        while (connected.value) {
            delay(quietMs)
            val outputNow = outputEvents.value
            val txNow = lastTxAtMs
            if (outputNow == lastOutputEvent && txNow == lastTx) return@withTimeoutOrNull true
            lastOutputEvent = outputNow
            lastTx = txNow
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
                    lastTxAtMs = System.currentTimeMillis()
                } catch (e: Exception) {
                    logger.log(LogLevel.WARN, TAG, "Send failed: ${e.javaClass.simpleName}: ${e.message} snapshot=${debugSnapshot()}")
                }
            }
        }
    }

    fun disconnect() {
        disconnectReason = "disconnect()"
        _connected.value = false
        keepaliveJob?.cancel()
        keepaliveJob = null
        scope.launch {
            logger.log(LogLevel.INFO, TAG, "Disconnecting from $serverLabel")
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
            logger.log(LogLevel.INFO, TAG, "Disconnected from $serverLabel")
        }
    }

    private fun startKeepaliveLoop(conn: Connection) {
        keepaliveJob?.cancel()
        keepaliveJob = scope.launch {
            while (_connected.value) {
                delay(nextKeepaliveDelayMs())
                if (!_connected.value) break
                val settings = settingsRepository.settings.value
                if (!settings.sshKeepaliveEnabled) continue
                if (!shouldSendKeepalive(settings.backgroundKeepaliveScope)) continue
                try {
                    conn.sendIgnorePacket()
                } catch (e: Exception) {
                    logger.log(LogLevel.WARN, TAG, "Keepalive failed: ${e.javaClass.simpleName}: ${e.message}")
                }
            }
        }
    }

    private fun nextKeepaliveDelayMs(): Long {
        val runtimeState = runtimeRepository.state.value
        if (runtimeState.appInForeground) return FOREGROUND_KEEPALIVE_MS
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
            BackgroundKeepaliveScope.ALL_SESSIONS -> true
            BackgroundKeepaliveScope.ACTIVE_TAB_ONLY -> projectId != null && projectId == runtimeState.activeProjectId
        }
    }

    fun debugSnapshot(): String {
        val now = System.currentTimeMillis()
        return buildString {
            append("instance=").append(instanceId)
            append(",sessionId=").append(sessionId)
            append(",projectId=").append(projectId)
            append(",projectName=").append(projectName)
            append(",server=").append(serverLabel)
            append(",connected=").append(_connected.value)
            append(",connection=").append(connection?.let { System.identityHashCode(it) })
            append(",shell=").append(shellSession?.let { System.identityHashCode(it) })
            append(",createdAgoMs=").append(now - createdAtMs)
            append(",connectedAgoMs=").append(connectedAtMs?.let { now - it })
            append(",lastRxAgoMs=").append(lastRxAtMs?.let { now - it })
            append(",lastTxAgoMs=").append(lastTxAtMs?.let { now - it })
            append(",lastResizeAgoMs=").append(lastResizeAtMs?.let { now - it })
            append(",disconnectReason=").append(disconnectReason)
        }
    }

    private fun describeBytes(bytes: ByteArray): String {
        val shown = bytes.take(24)
        return shown.joinToString(prefix = "[", postfix = if (bytes.size > shown.size) " ...]" else "]") {
            "%02x".format(it)
        }
    }

    private fun describeConditions(conditions: Int): String {
        val labels = mutableListOf<String>()
        if ((conditions and ChannelCondition.STDOUT_DATA) != 0) labels += "STDOUT_DATA"
        if ((conditions and ChannelCondition.STDERR_DATA) != 0) labels += "STDERR_DATA"
        if ((conditions and ChannelCondition.EOF) != 0) labels += "EOF"
        if ((conditions and ChannelCondition.EXIT_STATUS) != 0) labels += "EXIT_STATUS"
        if ((conditions and ChannelCondition.CLOSED) != 0) labels += "CLOSED"
        if ((conditions and ChannelCondition.TIMEOUT) != 0) labels += "TIMEOUT"
        return labels.joinToString("|")
    }

    companion object {
        private const val TAG = "SshConnection"
        private const val FOREGROUND_KEEPALIVE_MS = 60_000L
    }
}
