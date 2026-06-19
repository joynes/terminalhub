package se.joynes.terminalhub.data.model

data class SshSession(
    val id: String,
    val server: Server,
    val project: Project? = null,
    val isConnected: Boolean = false,
    val tmuxSessionName: String? = null
)
