package se.joynes.terminalhub.data.ssh

import com.trilead.ssh2.ChannelCondition
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import se.joynes.terminalhub.data.model.Server
import se.joynes.terminalhub.data.security.SecurePrefsManager

data class RemoteCommandResult(val output: String, val exitCode: Int, val truncated: Boolean = false, val error: String = "")
private const val MAX_OUTPUT = 1024 * 1024

/** One bounded exec channel at a time; never sends Git commands to the interactive PTY. */
class RemoteCommandExecutor @Inject constructor(
    private val pool: SharedSshTransportPool,
    private val credentials: SecurePrefsManager
) {
    suspend fun run(server: Server, command: String): RemoteCommandResult = withContext(Dispatchers.IO) {
        val lease = pool.acquireTransfer(server, credentials.getPassword(server.id), credentials.getPrivateKey(server.id))
        try {
            lease.openAuxiliarySession().use { channel ->
                coroutineScope {
                    val pending = async(Dispatchers.IO) {
                        val session = channel.session
                        val output = ByteArrayOutputStream()
                        val errors = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        session.execCommand(command)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            for ((input, destination) in listOf(session.stdout to output, session.stderr to errors)) {
                                if (input.available() > 0) {
                                    val count = input.read(buffer, 0, minOf(buffer.size, input.available()))
                                    if (count > 0) {
                                        val remaining = MAX_OUTPUT - output.size() - errors.size()
                                        destination.write(buffer, 0, minOf(count, remaining))
                                        if (count >= remaining) return@async RemoteCommandResult(output.toString("UTF-8"), -1, true)
                                    }
                                }
                            }
                            val conditions = session.waitForCondition(
                                ChannelCondition.STDOUT_DATA or ChannelCondition.STDERR_DATA or ChannelCondition.EOF or ChannelCondition.CLOSED, 100)
                            if (conditions and (ChannelCondition.EOF or ChannelCondition.CLOSED) != 0 &&
                                session.stdout.available() == 0 && session.stderr.available() == 0) {
                                session.waitForCondition(ChannelCondition.EXIT_STATUS, 300)
                                val code = session.exitStatus ?: throw IOException("SSH command channel closed before completion. Reconnect and retry.")
                                return@async RemoteCommandResult(output.toString("UTF-8"), code, error = errors.toString("UTF-8").trim().take(600))
                            }
                        }
                        @Suppress("UNREACHABLE_CODE") error("Unreachable")
                    }
                    // Closing only this channel also unblocks exec/read on cancellation or timeout.
                    try {
                        withTimeoutOrNull(20_000) { pending.await() }
                            ?: throw IOException("Remote command took too long. Try again.")
                    } finally {
                        channel.close()
                        pending.cancel()
                    }
                }
            }
        } finally { lease.release() }
    }
}
