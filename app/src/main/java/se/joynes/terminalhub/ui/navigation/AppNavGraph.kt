package se.joynes.terminalhub.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import se.joynes.terminalhub.BuildConfig
import se.joynes.terminalhub.ui.screen.applog.AppLogScreen
import se.joynes.terminalhub.ui.screen.projects.AddEditProjectScreen
import se.joynes.terminalhub.ui.screen.settings.SettingsScreen
import se.joynes.terminalhub.ui.screen.servers.AddEditServerScreen
import se.joynes.terminalhub.ui.screen.servers.ServerListScreen
import se.joynes.terminalhub.ui.screen.sessions.SessionHostScreen
import se.joynes.terminalhub.ui.screen.sessionlog.SessionLogScreen
import se.joynes.terminalhub.ui.screen.splash.SplashScreen
import se.joynes.terminalhub.ui.screen.status.ServerStatusScreen
import se.joynes.terminalhub.ui.screen.upload.FileUploadScreen

@Composable
fun AppNavGraph(
    sharedUri: Uri? = null,
    onConsumeSharedUri: () -> Unit = {}
) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Screen.Splash.route) {
        composable(Screen.Splash.route) {
            SplashScreen(onAuthSuccess = {
                navController.navigate(Screen.SessionHost.createRoute()) {
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
        composable(Screen.ServerList.route) {
            ServerListScreen(
                onAddServer = { navController.navigate(Screen.AddEditServer.createRoute()) },
                onEditServer = { id -> navController.navigate(Screen.AddEditServer.createRoute(id)) },
                onOpenTerminal = { id ->
                    navController.navigate(Screen.SessionHost.createRoute(id)) {
                        launchSingleTop = true
                    }
                },
                onOpenStatus = { id -> navController.navigate(Screen.ServerStatus.createRoute(id)) },
                onOpenUpload = { id -> navController.navigate(Screen.FileUpload.createRoute(id)) },
                onOpenLog = { navController.navigate(Screen.AppLog.route) },
                onOpenSessionLog = { navController.navigate(Screen.SessionLog.route) }
            )
        }
        composable(
            Screen.AddEditProject.route,
            arguments = listOf(
                navArgument("projectId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("serverId") { type = NavType.LongType; defaultValue = -1L }
            )
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getLong("projectId")?.takeIf { it >= 0 }
            val serverId = backStackEntry.arguments?.getLong("serverId")?.takeIf { it >= 0 }
            AddEditProjectScreen(serverId = serverId, projectId = projectId, onBack = { navController.popBackStack() })
        }
        composable(
            Screen.SessionHost.route,
            arguments = listOf(navArgument("serverId") { type = NavType.LongType; defaultValue = -1L })
        ) { backStackEntry ->
            val requestedServerId = backStackEntry.arguments?.getLong("serverId")?.takeIf { it >= 0 }
            val viewModel = androidx.hilt.navigation.compose.hiltViewModel<se.joynes.terminalhub.ui.screen.sessions.SessionHostViewModel>()
            val serverId by viewModel.serverId.collectAsState()
            SessionHostScreen(
                requestedServerId = requestedServerId,
                viewModel = viewModel,
                sharedUri = sharedUri,
                onConsumeSharedUri = onConsumeSharedUri,
                onOpenServers = { navController.navigate(Screen.ServerList.route) },
                onAddServer = {
                    if (BuildConfig.IS_DIAGNOSTIC) {
                        navController.navigate(Screen.AddEditProject.createRoute())
                    } else {
                        navController.navigate(Screen.AddEditServer.createRoute())
                    }
                },
                onAddProject = { navController.navigate(Screen.AddEditProject.createRoute(serverId)) },
                onOpenLogs = { navController.navigate(Screen.AppLog.route) },
                onOpenSettings = { navController.navigate(Screen.Settings.route) }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
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
