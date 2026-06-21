package se.joynes.terminalhub.ui.screen.settings

import android.app.ActivityManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import se.joynes.terminalhub.data.settings.BackgroundKeepaliveProfile
import se.joynes.terminalhub.data.settings.BackgroundKeepaliveScope
import se.joynes.terminalhub.ui.components.RetroButton
import se.joynes.terminalhub.ui.components.RetroCard
import se.joynes.terminalhub.ui.components.RetroTopBar
import se.joynes.terminalhub.ui.theme.MegaDriveBg
import se.joynes.terminalhub.ui.theme.MegaDriveDim
import se.joynes.terminalhub.ui.theme.MegaDriveOnSurface
import se.joynes.terminalhub.ui.theme.MegaDrivePrimary
import se.joynes.terminalhub.ui.theme.MegaDriveSurface
import se.joynes.terminalhub.ui.theme.MonoFontFamily
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenSessions: () -> Unit,
    onOpenServers: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val runtimeState by viewModel.runtimeState.collectAsState()
    val powerManager = context.getSystemService(PowerManager::class.java)
    val activityManager = context.getSystemService(ActivityManager::class.java)
    val packageName = context.packageName
    val batteryOptimizationIgnored = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        powerManager?.isIgnoringBatteryOptimizations(packageName) == true
    } else {
        true
    }
    val backgroundRestricted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        activityManager?.isBackgroundRestricted == true
    } else {
        false
    }
    val notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    fun formatTs(value: Long?): String =
        value?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(timeFormatter) } ?: "None"

    fun openIntent(vararg candidates: Intent) {
        val launched = candidates.firstOrNull { intent ->
            runCatching {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }.isSuccess
        }
        if (launched == null) {
            val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        }
    }

    fun openBatteryOptimizationRequest() {
        val requestIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        val generalIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        openIntent(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !batteryOptimizationIgnored) requestIntent else generalIntent,
            generalIntent
        )
    }

    fun openAppInfo() {
        openIntent(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
        )
    }

    fun openNotificationSettings() {
        openIntent(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                putExtra("app_package", packageName)
                putExtra("app_uid", context.applicationInfo.uid)
            },
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
        )
    }

    fun openGeneralBatterySettings() {
        openIntent(
            Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
    }

    Scaffold(
        topBar = { RetroTopBar(title = "SETTINGS", onBack = onBack) },
        containerColor = MegaDriveBg
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MegaDriveBg)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    SettingsCard(
                        title = "NAVIGATION",
                        description = "Jump between active terminal sessions and server configuration from here."
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            RetroButton(
                                text = "SESSIONS",
                                onClick = onOpenSessions,
                                modifier = Modifier.fillMaxWidth()
                            )
                            RetroButton(
                                text = "SERVERS",
                                onClick = onOpenServers,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                item {
                    SettingsCard(
                        title = "BACKGROUND STATUS",
                        description = "Shows the runtime state the app will use to explain the next reconnect. If the process was killed in background, or if SSH transport dropped while the process stayed alive, that reason is stored here and in the app logs."
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SettingsValue("Foreground service", if (runtimeState.foregroundServiceRunning) "Running" else "Not running")
                            SettingsValue("App state", if (runtimeState.appInForeground) "Foreground" else "Background/unknown")
                            SettingsValue("Tracked remote projects", runtimeState.remoteProjectIds.sorted().joinToString().ifBlank { "None" })
                            SettingsValue("Recovery pending", if (runtimeState.recoveryPending) "Yes" else "No")
                            SettingsValue("Last restart reason", runtimeState.lastProcessRestartReason ?: "None recorded")
                            SettingsValue("Last service stop", runtimeState.lastServiceStopReason ?: "None recorded")
                            SettingsValue("Last service stop at", formatTs(runtimeState.lastServiceStopAt))
                            SettingsValue("Last SSH drop", runtimeState.lastSshDisconnectSummary ?: "None recorded")
                        }
                    }
                }
                item {
                    SettingsCard(
                        title = "FAST RESUME",
                        description = "Keeps terminal focus and redraw behavior snappier when the app returns to foreground. It no longer stays active in background, so it should not keep burning battery while the app is hidden."
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (settings.preferFastResume) "Enabled" else "Disabled",
                                color = MegaDriveOnSurface,
                                fontFamily = MonoFontFamily,
                                fontSize = 12.sp
                            )
                            Switch(
                                checked = settings.preferFastResume,
                                onCheckedChange = viewModel::setPreferFastResume
                            )
                        }
                    }
                }
                item {
                    SettingsCard(
                        title = "SSH KEEPALIVE",
                        description = "Sends SSH keepalive traffic to reduce silent disconnects. Foreground sends every 60 seconds. Background behavior below controls how aggressively the app keeps multiple sessions alive."
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (settings.sshKeepaliveEnabled) "Enabled" else "Disabled",
                                color = MegaDriveOnSurface,
                                fontFamily = MonoFontFamily,
                                fontSize = 12.sp
                            )
                            Switch(
                                checked = settings.sshKeepaliveEnabled,
                                onCheckedChange = viewModel::setSshKeepaliveEnabled
                            )
                        }
                    }
                }
                item {
                    SettingsCard(
                        title = "BACKGROUND KEEPALIVE PROFILE",
                        description = "Controls how often background SSH sessions send keepalive packets. Aggressive (30s) protects sessions best. Ultra battery saver (10 min) keeps sessions alive on most networks while using almost no power."
                    ) {
                        SettingsValue(
                            "Current profile",
                            when (settings.backgroundKeepaliveProfile) {
                                BackgroundKeepaliveProfile.AGGRESSIVE -> "Aggressive (30s)"
                                BackgroundKeepaliveProfile.BALANCED -> "Balanced (2 min)"
                                BackgroundKeepaliveProfile.BATTERY_SAVER -> "Battery saver (5 min)"
                                BackgroundKeepaliveProfile.ULTRA_BATTERY_SAVER -> "Ultra battery saver (10 min)"
                            }
                        )
                        Spacer(Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            RetroButton(
                                text = "AGGRESSIVE (30s)",
                                onClick = { viewModel.setBackgroundKeepaliveProfile(BackgroundKeepaliveProfile.AGGRESSIVE) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = settings.backgroundKeepaliveProfile != BackgroundKeepaliveProfile.AGGRESSIVE
                            )
                            RetroButton(
                                text = "BALANCED (2 MIN)",
                                onClick = { viewModel.setBackgroundKeepaliveProfile(BackgroundKeepaliveProfile.BALANCED) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = settings.backgroundKeepaliveProfile != BackgroundKeepaliveProfile.BALANCED
                            )
                            RetroButton(
                                text = "BATTERY SAVER (5 MIN)",
                                onClick = { viewModel.setBackgroundKeepaliveProfile(BackgroundKeepaliveProfile.BATTERY_SAVER) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = settings.backgroundKeepaliveProfile != BackgroundKeepaliveProfile.BATTERY_SAVER
                            )
                            RetroButton(
                                text = "ULTRA BATTERY SAVER (10 MIN)",
                                onClick = { viewModel.setBackgroundKeepaliveProfile(BackgroundKeepaliveProfile.ULTRA_BATTERY_SAVER) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = settings.backgroundKeepaliveProfile != BackgroundKeepaliveProfile.ULTRA_BATTERY_SAVER
                            )
                        }
                    }
                }
                item {
                    SettingsCard(
                        title = "BACKGROUND KEEPALIVE SCOPE",
                        description = "Controls how many SSH tabs get background protection. Protecting only the active tab usually cuts battery use sharply when many projects are open."
                    ) {
                        SettingsValue(
                            "Current scope",
                            when (settings.backgroundKeepaliveScope) {
                                BackgroundKeepaliveScope.ALL_SESSIONS -> "All SSH sessions"
                                BackgroundKeepaliveScope.ACTIVE_TAB_ONLY -> "Active tab only"
                            }
                        )
                        Spacer(Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            RetroButton(
                                text = "ACTIVE TAB ONLY",
                                onClick = { viewModel.setBackgroundKeepaliveScope(BackgroundKeepaliveScope.ACTIVE_TAB_ONLY) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = settings.backgroundKeepaliveScope != BackgroundKeepaliveScope.ACTIVE_TAB_ONLY
                            )
                            RetroButton(
                                text = "ALL SESSIONS",
                                onClick = { viewModel.setBackgroundKeepaliveScope(BackgroundKeepaliveScope.ALL_SESSIONS) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = settings.backgroundKeepaliveScope != BackgroundKeepaliveScope.ALL_SESSIONS
                            )
                        }
                    }
                }
                item {
                    SettingsCard(
                        title = "SYSTEM PROTECTION",
                        description = "These Android system settings have the biggest effect on whether TerminalHub survives in background. They do not guarantee no cold starts, but they materially reduce the chance."
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SettingsValue(
                                "Battery optimization",
                                if (batteryOptimizationIgnored) "Ignored for this app" else "Still optimized"
                            )
                            SettingsValue(
                                "Background restriction",
                                if (backgroundRestricted) "Restricted by system" else "Not reported as restricted"
                            )
                            SettingsValue(
                                "Notifications",
                                if (notificationsEnabled) "Enabled" else "Disabled"
                            )
                            SettingsValue(
                                "Why this matters",
                                "Foreground service, battery policy and notification visibility all affect whether Android keeps the app alive long enough to resume sessions."
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            RetroButton(
                                text = if (batteryOptimizationIgnored) "OPEN BATTERY SETTINGS" else "ALLOW BATTERY EXEMPTION",
                                onClick = ::openBatteryOptimizationRequest,
                                modifier = Modifier.fillMaxWidth()
                            )
                            RetroButton(
                                text = "OPEN APP INFO",
                                onClick = ::openAppInfo,
                                modifier = Modifier.fillMaxWidth()
                            )
                            RetroButton(
                                text = "OPEN NOTIFICATION SETTINGS",
                                onClick = ::openNotificationSettings,
                                modifier = Modifier.fillMaxWidth()
                            )
                            RetroButton(
                                text = "OPEN GENERAL BATTERY SETTINGS",
                                onClick = ::openGeneralBatterySettings,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                item {
                    SettingsCard(
                        title = "BATTERY OPTIMIZATION",
                        description = "Direct shortcut for requesting battery optimization exclusion. On many phones this is the single most important setting if you want fewer cold starts."
                    ) {
                        Text(
                            if (batteryOptimizationIgnored) {
                                "Status: Not optimized for battery"
                            } else {
                                "Status: Battery optimization may still stop background activity"
                            },
                            color = if (batteryOptimizationIgnored) MegaDrivePrimary else MaterialTheme.colorScheme.error,
                            fontFamily = MonoFontFamily,
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        RetroButton(
                            text = if (batteryOptimizationIgnored) "OPEN BATTERY SETTINGS" else "ALLOW BACKGROUND EXEMPTION",
                            onClick = ::openBatteryOptimizationRequest,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    description: String,
    content: @Composable () -> Unit
) {
    RetroCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MegaDriveSurface)
                .padding(12.dp)
        ) {
            Text(
                title,
                color = MegaDrivePrimary,
                fontFamily = MonoFontFamily,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                description,
                color = MegaDriveDim,
                fontFamily = MonoFontFamily,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SettingsValue(label: String, value: String) {
    Column {
        Text(label, color = MegaDriveDim, fontFamily = MonoFontFamily, fontSize = 11.sp)
        Spacer(Modifier.height(2.dp))
        Text(value, color = MegaDriveOnSurface, fontFamily = MonoFontFamily, fontSize = 12.sp)
    }
}
