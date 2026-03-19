package se.joynes.aiterminalhub.data.model

data class Project(
    val id: Long = 0,
    val serverId: Long,
    val name: String,
    val projectPath: String,
    val sessionName: String,
    val setupScript: String = ""
)
