package se.joynes.aiterminalhub.data.ssh

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import se.joynes.aiterminalhub.data.logging.AppLogger
import se.joynes.aiterminalhub.data.logging.LogEvent
import se.joynes.aiterminalhub.data.logging.LogLevel
import se.joynes.aiterminalhub.data.model.Server
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

class SshConnection @Inject constructor(
    private val logger: AppLogger
) {
    private var jschSession: Session? = null
    private var shellChannel: ChannelShell? = null
    private var outputStream: OutputStream? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _output = MutableSharedFlow<ByteArray>(replay = 200, extraBufferCapacity = 512)
    val output: SharedFlow<ByteArray> = _output.asSharedFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    val sessionId = java.util.UUID.randomUUID().toString()

    fun connect(server: Server, password: String?) {
        scope.launch {
            try {
                logger.log(LogLevel.DEBUG, TAG, "Connecting to ${server.host}:${server.port}", LogEvent.SshConnect(server.host, server.port))
                val jsch = JSch()
                val session = jsch.getSession(server.username, server.host, server.port)
                if (password != null) session.setPassword(password)
                session.setConfig("StrictHostKeyChecking", "no")
                session.connect(30_000)
                jschSession = session
                logger.log(LogLevel.INFO, TAG, "SSH session established to ${server.host}")

                val ch = session.openChannel("shell") as ChannelShell
                ch.setPtyType("xterm-256color")
                outputStream = ch.outputStream
                ch.connect()
                shellChannel = ch
                _connected.value = true
                logger.log(LogLevel.INFO, TAG, "Shell channel opened")
                readOutput(ch.inputStream)
            } catch (e: Exception) {
                logger.log(LogLevel.ERROR, TAG, "Connection failed: ${e.message}")
                _connected.value = false
            }
        }
    }

    /** Run a command silently via a non-PTY exec channel and wait for it to finish. */
    suspend fun runSilent(command: String) {
        val session = jschSession ?: return
        withContext(Dispatchers.IO) {
            try {
                val ch = session.openChannel("exec") as ChannelExec
                // Use login shell so ~ expands and PATH includes tmux/brew paths
                ch.setCommand("bash -lc '${command.replace("'", "'\\''")}'")
                val errStream = ch.errStream  // capture stderr before connect
                ch.connect()
                val buf = ByteArray(1024)
                val stdout = ch.inputStream
                val stderr = errStream
                while (!ch.isClosed) {
                    while (stdout.available() > 0) stdout.read(buf)
                    if (stderr.available() > 0) {
                        val n = stderr.read(buf)
                        if (n > 0) logger.log(LogLevel.WARN, TAG, "Setup stderr: ${String(buf, 0, n)}")
                    }
                    delay(50)
                }
                val exitCode = ch.exitStatus
                ch.disconnect()
                logger.log(LogLevel.DEBUG, TAG, "Silent exec done (exit=$exitCode): $command")
            } catch (e: Exception) {
                logger.log(LogLevel.WARN, TAG, "Silent exec failed: ${e.message}")
            }
        }
    }

    /** Notify the server that the terminal has been resized. */
    fun resizePty(cols: Int, rows: Int) {
        shellChannel?.setPtySize(cols, rows, 0, 0)
        logger.log(LogLevel.DEBUG, TAG, "PTY resize: ${cols}x${rows}")
    }

    private fun readOutput(inputStream: InputStream) {
        scope.launch {
            val buffer = ByteArray(4096)
            try {
                while (_connected.value) {
                    val n = inputStream.read(buffer)
                    if (n < 0) break
                    logger.log(LogLevel.TRACE, TAG, "RX $n bytes", LogEvent.SshReceive(sessionId, n))
                    _output.emit(buffer.copyOf(n))
                }
            } catch (_: Exception) {}
            _connected.value = false
            logger.log(LogLevel.INFO, TAG, "SSH read loop ended")
        }
    }

    fun send(text: String) = sendBytes(text.toByteArray(Charsets.UTF_8))

    fun sendBytes(bytes: ByteArray) {
        scope.launch {
            try {
                outputStream?.write(bytes)
                outputStream?.flush()
                logger.log(LogLevel.TRACE, TAG, "TX ${bytes.size} bytes", LogEvent.SshSend(sessionId, bytes.size))
            } catch (e: Exception) {
                logger.log(LogLevel.WARN, TAG, "Send failed: ${e.message}")
            }
        }
    }

    fun disconnect() {
        _connected.value = false
        shellChannel?.disconnect()
        jschSession?.disconnect()
        shellChannel = null
        jschSession = null
        outputStream = null
        logger.log(LogLevel.INFO, TAG, "Disconnected")
    }

    companion object { private const val TAG = "SshConnection" }
}
