package se.joynes.terminalhub.ui.screen.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import se.joynes.terminalhub.data.settings.BackgroundKeepaliveProfile
import se.joynes.terminalhub.data.settings.BackgroundKeepaliveScope
import se.joynes.terminalhub.data.runtime.BackgroundSshMode
import se.joynes.terminalhub.ui.components.RetroButton
import se.joynes.terminalhub.ui.components.RetroCard
import se.joynes.terminalhub.ui.components.RetroTopBar
import se.joynes.terminalhub.ui.theme.MegaDriveBg
import se.joynes.terminalhub.ui.theme.MegaDriveDim
import se.joynes.terminalhub.ui.theme.MegaDriveOnSurface
import se.joynes.terminalhub.ui.theme.MegaDrivePrimary
import se.joynes.terminalhub.ui.theme.MegaDriveSurface
import se.joynes.terminalhub.ui.theme.MonoFontFamily

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenSessions: () -> Unit,
    onOpenServers: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val runtimeState by viewModel.runtimeState.collectAsState()
    val backgroundSshMode by viewModel.backgroundSshMode.collectAsState()
    val context = LocalContext.current
    var showBackgroundSshConfirmation by remember { mutableStateOf(false) }

    fun showStartResult(result: BackgroundSshStartResult) {
        val message = when (result) {
            BackgroundSshStartResult.STARTED -> "Background SSH started"
            BackgroundSshStartResult.NO_ACTIVE_SSH_SESSIONS -> "Open an SSH terminal first"
            BackgroundSshStartResult.NOTIFICATION_PERMISSION_REQUIRED -> "Notification permission is required"
            BackgroundSshStartResult.START_FAILED -> "Could not start background SSH"
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showStartResult(viewModel.startBackgroundSsh(notificationPermissionGranted = true))
        } else {
            viewModel.notificationPermissionDenied()
            showStartResult(BackgroundSshStartResult.NOTIFICATION_PERMISSION_REQUIRED)
        }
    }

    fun requestBackgroundSshStart() {
        val notificationGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (notificationGranted) {
            showStartResult(viewModel.startBackgroundSsh(notificationPermissionGranted = true))
        } else {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    if (showBackgroundSshConfirmation) {
        AlertDialog(
            onDismissRequest = { showBackgroundSshConfirmation = false },
            title = { Text("KEEP SSH ACTIVE IN BACKGROUND?") },
            text = {
                Text(
                    "TerminalHub will show an ongoing notification and may use battery and mobile data. " +
                        "You can stop it from Settings or the notification; stopping closes SSH transports " +
                        "but leaves tmux sessions running. Android or the network can still interrupt it, " +
                        "so tmux remains the reliable fallback."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showBackgroundSshConfirmation = false
                    requestBackgroundSshStart()
                }) { Text("START") }
            },
            dismissButton = {
                TextButton(onClick = { showBackgroundSshConfirmation = false }) { Text("CANCEL") }
            }
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
                        description = "Shows the current process and explicitly user-started background SSH state. tmux remains the fallback if Android or the network interrupts the app."
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SettingsValue("App state", if (runtimeState.appInForeground) "Foreground" else "Background/unknown")
                            SettingsValue("Background SSH", backgroundSshMode.name.lowercase().replaceFirstChar { it.uppercase() })
                            SettingsValue("Foreground service", if (runtimeState.foregroundServiceRunning) "Running" else "Stopped")
                            SettingsValue("Tracked remote projects", runtimeState.remoteProjectIds.sorted().joinToString().ifBlank { "None" })
                            SettingsValue("Recovery pending", if (runtimeState.recoveryPending) "Yes" else "No")
                            SettingsValue("Last restart reason", runtimeState.lastProcessRestartReason ?: "None recorded")
                            SettingsValue("Last SSH drop", runtimeState.lastSshDisconnectSummary ?: "None recorded")
                        }
                    }
                }
                item {
                    SettingsCard(
                        title = "KEEP SSH ACTIVE IN BACKGROUND",
                        description = "Optional and off by default. Starts only after you enable it here while an SSH tab is open. It shows an ongoing notification and may use battery and mobile data."
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (settings.keepSshActiveInBackground) "Active" else "Off",
                                color = MegaDriveOnSurface,
                                fontFamily = MonoFontFamily,
                                fontSize = 12.sp
                            )
                            Switch(
                                checked = settings.keepSshActiveInBackground,
                                onCheckedChange = { enabled ->
                                    if (enabled) showBackgroundSshConfirmation = true
                                    else viewModel.stopBackgroundSsh()
                                }
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        RetroButton(
                            text = "ANDROID BATTERY SETTINGS",
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                }.onFailure {
                                    Toast.makeText(context, "Battery settings are unavailable", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Optional: review Android battery settings if connections are interrupted. TerminalHub never requests exemption automatically.",
                            color = MegaDriveDim,
                            fontFamily = MonoFontFamily,
                            fontSize = 11.sp
                        )
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
                        title = "TEXT INPUT ENTER",
                        description = "When enabled, Send/Enter in the large text input also sends Enter to the terminal, so the command runs immediately. Keep it disabled when composing multi-line text or when you want to review the command in the terminal first."
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (settings.executeTextInputOnSend) "Execute immediately" else "Send text only",
                                color = MegaDriveOnSurface,
                                fontFamily = MonoFontFamily,
                                fontSize = 12.sp
                            )
                            Switch(
                                checked = settings.executeTextInputOnSend,
                                onCheckedChange = viewModel::setExecuteTextInputOnSend
                            )
                        }
                    }
                }
                item {
                    SettingsCard(
                        title = "TERMINAL KEY BAR",
                        description = "Tap a key to replace or remove it. Add, delete or reorder up to ${se.joynes.terminalhub.data.settings.KeyBarLayoutConfig.MAX_ROWS} rows. The layout is included in app export/import backups."
                    ) {
                        KeyBarSettingsEditor(
                            rows = settings.keyBarRows,
                            onRowsChange = viewModel::setKeyBarRows
                        )
                    }
                }
                item {
                    SettingsCard(
                        title = "SSH KEEPALIVE",
                        description = "Sends SSH keepalive traffic to reduce silent disconnects while Android keeps the app process alive. tmux preserves remote work if Android stops the process."
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
                        description = "Controls opportunistic keepalive timing while the app remains in memory. Android may still stop background networking; TerminalHub then reconnects to the tmux session when reopened."
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
                        description = "Controls which SSH tabs receive opportunistic keepalives while the app remains in memory. Active tab only uses the least background network traffic."
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
