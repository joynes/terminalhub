package se.joynes.aiterminalhub.ui.screen.settings

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
import androidx.hilt.navigation.compose.hiltViewModel
import se.joynes.aiterminalhub.ui.components.RetroButton
import se.joynes.aiterminalhub.ui.components.RetroCard
import se.joynes.aiterminalhub.ui.components.RetroTopBar
import se.joynes.aiterminalhub.ui.theme.MegaDriveBg
import se.joynes.aiterminalhub.ui.theme.MegaDriveDim
import se.joynes.aiterminalhub.ui.theme.MegaDriveOnSurface
import se.joynes.aiterminalhub.ui.theme.MegaDrivePrimary
import se.joynes.aiterminalhub.ui.theme.MegaDriveSurface
import se.joynes.aiterminalhub.ui.theme.MonoFontFamily
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val runtimeState by viewModel.runtimeState.collectAsState()
    val powerManager = context.getSystemService(PowerManager::class.java)
    val packageName = context.packageName
    val batteryOptimizationIgnored = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        powerManager?.isIgnoringBatteryOptimizations(packageName) == true
    } else {
        true
    }
    val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    fun formatTs(value: Long?): String =
        value?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(timeFormatter) } ?: "None"

    fun openBatteryOptimizationRequest() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(PowerManager::class.java)
            if (pm?.isIgnoringBatteryOptimizations(packageName) == true) {
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            } else {
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure {
                val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
            }
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
                        description = "Keeps terminal redraw and focus behavior more active while the app is open, so returning to the app feels closer to the older behavior. This helps the terminal come back faster, but it does not keep Android from killing the process."
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
                        description = "Sends a lightweight keepalive packet roughly every 30 seconds while an SSH connection is open. This reduces silent disconnects when routers, mobile networks or the phone idle out quiet connections."
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
                        title = "BATTERY OPTIMIZATION",
                        description = "Android vendor battery controls can still stop the app or network in the background. Excluding AITerminalHub from battery optimization improves the chance that sessions stay reachable when you switch apps."
                    ) {
                        Text(
                            if (batteryOptimizationIgnored) {
                                "Status: Not optimized for battery"
                            } else {
                                "Status: Battery optimization may stop background activity"
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
