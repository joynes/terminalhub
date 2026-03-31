package se.joynes.aiterminalhub.data.model

enum class ProjectTargetType {
    SSH,
    LOCAL
}

const val LOCAL_PROJECT_SERVER_ID = -1L

data class Project(
    val id: Long = 0,
    val serverId: Long,
    val targetType: ProjectTargetType = ProjectTargetType.SSH,
    val name: String,
    val useTmux: Boolean = true,
    val customScript: String = "cd {{PROJECT_PATH}}",
    val aiCommand: String = "",
    val colorSeed: Int = 0,
    val gitUrl: String = ""
) {
    val isLocal: Boolean get() = targetType == ProjectTargetType.LOCAL
}
