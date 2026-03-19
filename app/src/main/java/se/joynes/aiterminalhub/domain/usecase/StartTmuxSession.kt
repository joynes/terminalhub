package se.joynes.aiterminalhub.domain.usecase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import se.joynes.aiterminalhub.data.ssh.SshManager
import se.joynes.aiterminalhub.data.ssh.TmuxIntegration
import javax.inject.Inject

class StartTmuxSession @Inject constructor(
    private val sshManager: SshManager,
    private val tmux: TmuxIntegration
) {
    suspend operator fun invoke(sessionId: String, tmuxName: String): Boolean = withContext(Dispatchers.IO) {
        // This would need the underlying JSch session; for now send via shell
        val conn = sshManager.getSession(sessionId) ?: return@withContext false
        conn.send("tmux new-session -d -s '$tmuxName' 2>/dev/null || true\n")
        conn.send("tmux attach-session -t '$tmuxName'\n")
        true
    }
}
