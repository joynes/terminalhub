package se.joynes.aiterminalhub.data.model

data class Project(
    val id: Long = 0,
    val serverId: Long,
    val name: String,
    // null = use server's default setupScript; empty string = run nothing on connect
    val setupScript: String? = null,
    val colorSeed: Int = 0
)
