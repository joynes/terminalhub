package se.joynes.terminalhub.data.ssh

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import se.joynes.terminalhub.data.logging.AppLogger
import se.joynes.terminalhub.data.logging.LogLevel
import javax.inject.Inject

class TmuxIntegration @Inject constructor(
    private val logger: AppLogger
) {
    private val TAG = "TmuxIntegration"

    suspend fun hasTmux(session: Session): Boolean = withContext(Dispatchers.IO) {
        !execCommand(session, "which tmux").isNullOrBlank()
    }

    suspend fun listSessions(session: Session): List<String> = withContext(Dispatchers.IO) {
        execCommand(session, "tmux list-sessions -F '#S' 2>/dev/null || echo ''")
            .orEmpty().lines().filter { it.isNotBlank() }
    }

    suspend fun newSession(session: Session, name: String): Boolean = withContext(Dispatchers.IO) {
        val result = execCommand(session, "tmux new-session -d -s ${TmuxWindowCommands.quote(name)} 2>&1")
        result != null && !result.contains("duplicate session")
    }

    suspend fun attachSession(session: Session, name: String): Boolean = withContext(Dispatchers.IO) {
        execCommand(session, "tmux has-session -t ${TmuxWindowCommands.quote(name)} 2>&1")?.isBlank() == true
    }

    suspend fun sendKeys(session: Session, tmuxSession: String, keys: String) = withContext(Dispatchers.IO) {
        execCommand(session, "tmux send-keys -t ${TmuxWindowCommands.quote(tmuxSession)} ${TmuxWindowCommands.quote(keys)} Enter").orEmpty()
    }

    private fun execCommand(session: Session, cmd: String): String? {
        var channel: ChannelExec? = null
        return try {
            channel = session.openChannel("exec") as ChannelExec
            channel.setCommand(cmd)
            channel.connect()
            val output = channel.inputStream.bufferedReader().readText()
            if (channel.exitStatus > 0) null else output.trim()
        } catch (e: Exception) {
            logger.log(LogLevel.ERROR, TAG, "exec failed: ${e.message}")
            null
        } finally {
            channel?.disconnect()
        }
    }
}
