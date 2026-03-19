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
        const val DEFAULT_SETUP_SCRIPT =
            "mkdir -p {{PROJECT_PATH}}\n" +
            "tmux has-session -t {{SESSION_NAME}} 2>/dev/null || tmux new-session -d -s {{SESSION_NAME}}\n" +
            "tmux send-keys -t {{SESSION_NAME}} \"cd {{PROJECT_PATH}}\" Enter\n" +
            "tmux attach -t {{SESSION_NAME}}"
    }
}
