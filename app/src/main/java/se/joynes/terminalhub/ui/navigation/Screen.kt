package se.joynes.terminalhub.ui.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object ServerList : Screen("server_list")
    object AddEditServer : Screen("add_edit_server?serverId={serverId}") {
        fun createRoute(serverId: Long? = null) = "add_edit_server?serverId=${serverId ?: -1}"
    }
    object ProjectList : Screen("project_list?serverId={serverId}") {
        fun createRoute(serverId: Long) = "project_list?serverId=$serverId"
    }
    object AddEditProject : Screen("add_edit_project?projectId={projectId}&serverId={serverId}") {
        fun createRoute(serverId: Long? = null, projectId: Long? = null) =
            "add_edit_project?projectId=${projectId ?: -1}&serverId=${serverId ?: -1}"
    }
    object SessionHost : Screen("session_host?serverId={serverId}&projectId={projectId}") {
        fun createRoute(serverId: Long? = null, projectId: Long? = null) =
            "session_host?serverId=${serverId ?: -1}&projectId=${projectId ?: -1}"
    }
    object ServerStatus : Screen("server_status?serverId={serverId}") {
        fun createRoute(serverId: Long) = "server_status?serverId=$serverId"
    }
    object FileUpload : Screen("file_upload?serverId={serverId}") {
        fun createRoute(serverId: Long) = "file_upload?serverId=$serverId"
    }
    object Settings : Screen("settings")
    object SessionLog : Screen("session_log")
    object AppLog : Screen("app_log")
}
