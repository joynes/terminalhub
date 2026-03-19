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
    private var execChannel: ChannelExec? = null
    private var outputStream: OutputStream? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _output = MutableSharedFlow<String>(replay = 200, extraBufferCapacity = 512)
    val output: SharedFlow<String> = _output.asSharedFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    val sessionId = java.util.UUID.randomUUID().toString()

    // command = null  → plain interactive shell (ChannelShell)
    // command = "..." → exec with PTY; setup commands run silently, then e.g. tmux attach
    fun connect(server: Server, password: String?, command: String? = null) {
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

                val inputStream = if (command != null) {
                    val ch = session.openChannel("exec") as ChannelExec
                    ch.setCommand(command)
                    ch.setPty(true)
                    ch.setPtyType("xterm-256color")
                    outputStream = ch.outputStream
                    ch.connect()
                    execChannel = ch
                    logger.log(LogLevel.DEBUG, TAG, "Exec channel opened: $command")
                    ch.inputStream
                } else {
                    val ch = session.openChannel("shell") as ChannelShell
                    ch.setPtyType("xterm-256color")
                    outputStream = ch.outputStream
                    ch.connect()
                    shellChannel = ch
                    ch.inputStream
                }
                _connected.value = true
                logger.log(LogLevel.INFO, TAG, "Shell channel opened")
                readOutput(inputStream)
            } catch (e: Exception) {
                logger.log(LogLevel.ERROR, TAG, "Connection failed: ${e.message}")
                _connected.value = false
            }
        }
    }

    private fun readOutput(inputStream: InputStream) {
        scope.launch {
            val buffer = ByteArray(4096)
            try {
                while (_connected.value) {
                    val n = inputStream.read(buffer)
                    if (n < 0) break
                    val text = String(buffer, 0, n, Charsets.UTF_8)
                    logger.log(LogLevel.TRACE, TAG, "RX $n bytes", LogEvent.SshReceive(sessionId, n))
                    _output.emit(text)
                }
            } catch (_: Exception) {}
            _connected.value = false
            logger.log(LogLevel.INFO, TAG, "SSH read loop ended")
        }
    }

    fun send(text: String) {
        scope.launch {
            try {
                val bytes = text.toByteArray(Charsets.UTF_8)
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
        execChannel?.disconnect()
        jschSession?.disconnect()
        shellChannel = null
        execChannel = null
        jschSession = null
        outputStream = null
        logger.log(LogLevel.INFO, TAG, "Disconnected")
    }

    companion object { private const val TAG = "SshConnection" }
}
