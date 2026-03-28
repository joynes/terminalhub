package se.joynes.aiterminalhub.data.ssh

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import se.joynes.aiterminalhub.data.logging.AppLogger
import se.joynes.aiterminalhub.data.logging.LogLevel
import javax.inject.Inject

class TmuxIntegration @Inject constructor(
    private val logger: AppLogger
) {
    private val TAG = "TmuxIntegration"

    suspend fun hasTmux(session: Session): Boolean = withContext(Dispatchers.IO) {
        execCommand(session, "which tmux").isNotBlank()
    }

    suspend fun listSessions(session: Session): List<String> = withContext(Dispatchers.IO) {
        execCommand(session, "tmux list-sessions -F '#S' 2>/dev/null || echo ''")
            .lines().filter { it.isNotBlank() }
    }

    suspend fun newSession(session: Session, name: String): Boolean = withContext(Dispatchers.IO) {
        val result = execCommand(session, "tmux new-session -d -s '$name' 2>&1")
        !result.contains("duplicate session")
    }

    suspend fun attachSession(session: Session, name: String): Boolean = withContext(Dispatchers.IO) {
        execCommand(session, "tmux has-session -t '$name' 2>&1").isBlank()
    }

    suspend fun sendKeys(session: Session, tmuxSession: String, keys: String) = withContext(Dispatchers.IO) {
        execCommand(session, "tmux send-keys -t '$tmuxSession' '${keys.replace("'", "\\'")}' Enter")
    }

    private fun execCommand(session: Session, cmd: String): String {
        return try {
            val channel = session.openChannel("exec") as ChannelExec
            channel.setCommand(cmd)
            channel.connect()
            val output = channel.inputStream.bufferedReader().readText()
            channel.disconnect()
            output.trim()
        } catch (e: Exception) {
            logger.log(LogLevel.ERROR, TAG, "exec failed: ${e.message}")
            ""
        }
    }
}
