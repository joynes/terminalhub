package se.joynes.aiterminalhub.data.ssh

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import se.joynes.aiterminalhub.data.logging.AppLogger
import se.joynes.aiterminalhub.data.logging.LogEvent
import se.joynes.aiterminalhub.data.logging.LogLevel
import javax.inject.Inject

class TmuxIntegration @Inject constructor(
    private val logger: AppLogger
) {
    private val TAG = "TmuxIntegration"

    suspend fun hasTmux(session: Session): Boolean = withContext(Dispatchers.IO) {
        val result = execCommand(session, "which tmux")
        val has = result.isNotBlank()
        logger.log(LogLevel.DEBUG, TAG, "tmux available: $has")
        has
    }

    suspend fun listSessions(session: Session): List<String> = withContext(Dispatchers.IO) {
        val output = execCommand(session, "tmux list-sessions -F '#S' 2>/dev/null || echo ''")
        logger.log(LogLevel.DEBUG, TAG, "tmux sessions: $output")
        output.lines().filter { it.isNotBlank() }
    }

    suspend fun newSession(session: Session, name: String): Boolean = withContext(Dispatchers.IO) {
        logger.log(LogLevel.DEBUG, TAG, "Creating tmux session: $name", LogEvent.TmuxCmd(name, "new-session"))
        val result = execCommand(session, "tmux new-session -d -s '$name' 2>&1")
        val success = !result.contains("duplicate session")
        logger.log(if (success) LogLevel.INFO else LogLevel.WARN, TAG, "tmux new-session '$name': $result")
        success
    }

    suspend fun attachSession(session: Session, name: String): Boolean = withContext(Dispatchers.IO) {
        logger.log(LogLevel.DEBUG, TAG, "Attaching tmux session: $name", LogEvent.TmuxCmd(name, "attach"))
        val result = execCommand(session, "tmux has-session -t '$name' 2>&1")
        result.isBlank()
    }

    suspend fun sendKeys(session: Session, tmuxSession: String, keys: String) = withContext(Dispatchers.IO) {
        logger.log(LogLevel.DEBUG, TAG, "Sending keys to tmux '$tmuxSession': $keys", LogEvent.TmuxCmd(tmuxSession, keys))
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
