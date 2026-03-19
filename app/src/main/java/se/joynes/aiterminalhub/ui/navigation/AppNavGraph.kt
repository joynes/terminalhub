package se.joynes.aiterminalhub.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import se.joynes.aiterminalhub.ui.screen.applog.AppLogScreen
import se.joynes.aiterminalhub.ui.screen.projects.AddEditProjectScreen
import se.joynes.aiterminalhub.ui.screen.projects.ProjectListScreen
import se.joynes.aiterminalhub.ui.screen.servers.AddEditServerScreen
import se.joynes.aiterminalhub.ui.screen.servers.ServerListScreen
import se.joynes.aiterminalhub.ui.screen.sessions.SessionHostScreen
import se.joynes.aiterminalhub.ui.screen.sessionlog.SessionLogScreen
import se.joynes.aiterminalhub.ui.screen.splash.SplashScreen
import se.joynes.aiterminalhub.ui.screen.status.ServerStatusScreen
import se.joynes.aiterminalhub.ui.screen.upload.FileUploadScreen

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Screen.Splash.route) {
        composable(Screen.Splash.route) {
            SplashScreen(onAuthSuccess = {
                navController.navigate(Screen.ServerList.route) {
                    popUpTo(Screen.Splash.route) { inclusive = true }
                }
            })
        }
        composable(Screen.ServerList.route) {
            ServerListScreen(
                onAddServer = { navController.navigate(Screen.AddEditServer.createRoute()) },
                onEditServer = { id -> navController.navigate(Screen.AddEditServer.createRoute(id)) },
                onOpenProjects = { id -> navController.navigate(Screen.ProjectList.createRoute(id)) },
                onOpenStatus = { id -> navController.navigate(Screen.ServerStatus.createRoute(id)) },
                onOpenUpload = { id -> navController.navigate(Screen.FileUpload.createRoute(id)) },
                onOpenLog = { navController.navigate(Screen.AppLog.route) }
            )
        }
        composable(
            Screen.AddEditServer.route,
            arguments = listOf(navArgument("serverId") { type = NavType.LongType; defaultValue = -1L })
        ) { backStackEntry ->
            val serverId = backStackEntry.arguments?.getLong("serverId")?.takeIf { it >= 0 }
            AddEditServerScreen(serverId = serverId, onBack = { navController.popBackStack() })
        }
        composable(
            Screen.ProjectList.route,
            arguments = listOf(navArgument("serverId") { type = NavType.LongType })
        ) { backStackEntry ->
            val serverId = backStackEntry.arguments?.getLong("serverId") ?: return@composable
            ProjectListScreen(
                serverId = serverId,
                onAddProject = { navController.navigate(Screen.AddEditProject.createRoute(serverId)) },
                onEditProject = { pid -> navController.navigate(Screen.AddEditProject.createRoute(serverId, pid)) },
                onConnect = { pid -> navController.navigate(Screen.SessionHost.createRoute(serverId, pid)) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            Screen.AddEditProject.route,
            arguments = listOf(
                navArgument("projectId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("serverId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getLong("projectId")?.takeIf { it >= 0 }
            val serverId = backStackEntry.arguments?.getLong("serverId") ?: return@composable
            AddEditProjectScreen(serverId = serverId, projectId = projectId, onBack = { navController.popBackStack() })
        }
        composable(
            Screen.SessionHost.route,
            arguments = listOf(
                navArgument("serverId") { type = NavType.LongType },
                navArgument("projectId") { type = NavType.LongType; defaultValue = -1L }
            )
        ) { backStackEntry ->
            val serverId = backStackEntry.arguments?.getLong("serverId") ?: return@composable
            val projectId = backStackEntry.arguments?.getLong("projectId")?.takeIf { it >= 0 }
            SessionHostScreen(serverId = serverId, projectId = projectId, onBack = { navController.popBackStack() })
        }
        composable(
            Screen.ServerStatus.route,
            arguments = listOf(navArgument("serverId") { type = NavType.LongType })
        ) { backStackEntry ->
            val serverId = backStackEntry.arguments?.getLong("serverId") ?: return@composable
            ServerStatusScreen(serverId = serverId, onBack = { navController.popBackStack() })
        }
        composable(
            Screen.FileUpload.route,
            arguments = listOf(navArgument("serverId") { type = NavType.LongType })
        ) { backStackEntry ->
            val serverId = backStackEntry.arguments?.getLong("serverId") ?: return@composable
            FileUploadScreen(serverId = serverId, onBack = { navController.popBackStack() })
        }
        composable(Screen.SessionLog.route) {
            SessionLogScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.AppLog.route) {
            AppLogScreen(onBack = { navController.popBackStack() })
        }
    }
}
