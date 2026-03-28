package se.joynes.aiterminalhub.data.model

data class Project(
    val id: Long = 0,
    val serverId: Long,
    val name: String,
    val useTmux: Boolean = true,
    val customScript: String = "cd {{PROJECT_PATH}}",
    val aiCommand: String = "",
    val colorSeed: Int = 0,
    val gitUrl: String = ""
)
