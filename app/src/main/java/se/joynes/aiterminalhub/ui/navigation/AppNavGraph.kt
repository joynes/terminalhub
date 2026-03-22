package se.joynes.aiterminalhub.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import se.joynes.aiterminalhub.ui.screen.applog.AppLogScreen
import se.joynes.aiterminalhub.ui.screen.projects.AddEditProjectScreen
import se.joynes.aiterminalhub.ui.screen.servers.AddEditServerScreen
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
                navController.navigate(Screen.SessionHost.route) {
                    popUpTo(Screen.Splash.route) { inclusive = true }
                }
            })
        }
        composable(
            Screen.AddEditServer.route,
            arguments = listOf(navArgument("serverId") { type = NavType.LongType; defaultValue = -1L })
        ) { backStackEntry ->
            val serverId = backStackEntry.arguments?.getLong("serverId")?.takeIf { it >= 0 }
            AddEditServerScreen(serverId = serverId, onBack = { navController.popBackStack() })
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
        composable(Screen.SessionHost.route) {
            val viewModel = androidx.hilt.navigation.compose.hiltViewModel<se.joynes.aiterminalhub.ui.screen.sessions.SessionHostViewModel>()
            val serverId by viewModel.serverId.collectAsState()
            SessionHostScreen(
                viewModel = viewModel,
                onEditServer = { serverId?.let { id -> navController.navigate(Screen.AddEditServer.createRoute(id)) } },
                onAddProject = { serverId?.let { id -> navController.navigate(Screen.AddEditProject.createRoute(id)) } },
                onOpenLogs = { navController.navigate(Screen.AppLog.route) }
            )
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
