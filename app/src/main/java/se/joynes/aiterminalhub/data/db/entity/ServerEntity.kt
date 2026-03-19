package se.joynes.aiterminalhub.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "servers")
data class ServerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val host: String,
    val port: Int = 22,
    val username: String,
    val authType: String = "password", // "password" or "key"
    val keyAlias: String? = null,
    val projectsFolder: String = "~/aiterminalhub",
    val setupScript: String = DEFAULT_SETUP_SCRIPT,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        // Runs silently via exec channel (no PTY) — creates dir + tmux session
        const val DEFAULT_SETUP_SCRIPT =
            "mkdir -p {{PROJECT_PATH}} 2>/dev/null; " +
            "tmux has-session -t {{SESSION_NAME}} 2>/dev/null || " +
            "tmux new-session -d -s {{SESSION_NAME}} -c {{PROJECT_PATH}}"

        // Sent to the interactive shell after the banner — attaches to the session
        const val DEFAULT_ATTACH_COMMAND = "tmux attach -t {{SESSION_NAME}}"
    }
}
