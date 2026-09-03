package se.joynes.terminalhub.ui.screen.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
import se.joynes.terminalhub.ui.theme.MegaDriveGreen
import se.joynes.terminalhub.ui.theme.MegaDriveOnSurface
import se.joynes.terminalhub.ui.theme.MegaDrivePrimary
import se.joynes.terminalhub.ui.theme.MegaDriveSurface
import se.joynes.terminalhub.ui.theme.MegaDriveWarning
import se.joynes.terminalhub.ui.theme.MonoFontFamily

internal enum class SettingsSectionId {
    CONNECTIONS,
    TERMINAL_INPUT,
    ADVANCED,
    STATUS
}

internal val settingsSectionOrder = listOf(
    SettingsSectionId.CONNECTIONS,
    SettingsSectionId.TERMINAL_INPUT,
    SettingsSectionId.ADVANCED,
    SettingsSectionId.STATUS
)

internal fun isSettingsSectionExpandedByDefault(section: SettingsSectionId): Boolean =
    section == SettingsSectionId.CONNECTIONS || section == SettingsSectionId.TERMINAL_INPUT

internal fun batteryOptimizationStatusLabel(exempt: Boolean): String =
    if (exempt) "Exemption detected" else "Not exempt — recommended"

private fun isBatteryOptimizationExempt(context: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
    val powerManager = context.getSystemService(PowerManager::class.java) ?: return false
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenSessions: () -> Unit,
    onOpenServers: () -> Unit,
    onReconnectAll: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val runtimeState by viewModel.runtimeState.collectAsState()
    val backgroundSshMode by viewModel.backgroundSshMode.collectAsState()
    val backgroundSshIsRunning = runtimeState.foregroundServiceRunning
    val backgroundSshStatus = when {
        !settings.keepSshActiveInBackground -> "Off"
        backgroundSshIsRunning -> "Active"
        else -> "Ready — tap Start"
    }
    val context = LocalContext.current
    var batteryOptimizationExempt by remember {
        mutableStateOf(isBatteryOptimizationExempt(context))
    }
    var showBackgroundSshConfirmation by remember { mutableStateOf(false) }
    var connectionsExpanded by rememberSaveable {
        mutableStateOf(isSettingsSectionExpandedByDefault(SettingsSectionId.CONNECTIONS))
    }
    var terminalInputExpanded by rememberSaveable {
        mutableStateOf(isSettingsSectionExpandedByDefault(SettingsSectionId.TERMINAL_INPUT))
    }
    var advancedExpanded by rememberSaveable {
        mutableStateOf(isSettingsSectionExpandedByDefault(SettingsSectionId.ADVANCED))
    }
    var statusExpanded by rememberSaveable {
        mutableStateOf(isSettingsSectionExpandedByDefault(SettingsSectionId.STATUS))
    }

    fun showStartResult(result: BackgroundSshStartResult) {
        val message = when (result) {
            BackgroundSshStartResult.STARTED -> "Background SSH started"
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

    val batterySettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        batteryOptimizationExempt = isBatteryOptimizationExempt(context)
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
                    "TerminalHub will show an ongoing notification, even when no SSH sessions are open, " +
                        "and may use battery and mobile data. " +
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
                    QuickAccessCard(
                        onOpenSessions = onOpenSessions,
                        onOpenServers = onOpenServers
                    )
                }
                item {
                    ExpandableSettingsSection(
                        title = "CONNECTION & BACKGROUND",
                        summary = "Background ${backgroundSshStatus.lowercase()} · SSH keepalive ${if (settings.sshKeepaliveEnabled) "on" else "off"}",
                        importance = "MOST IMPORTANT",
                        expanded = connectionsExpanded,
                        onExpandedChange = { connectionsExpanded = it }
                    ) {
                        SettingsToggleRow(
                            title = "Keep SSH active in background",
                            description = "Recommended when switching apps: without it, Android may disconnect SSH and you will need to reconnect. Your choice is remembered after updates and process restarts; tap Start to resume it.",
                            status = backgroundSshStatus,
                            checked = settings.keepSshActiveInBackground,
                            onCheckedChange = { enabled ->
                                if (enabled) showBackgroundSshConfirmation = true
                                else viewModel.stopBackgroundSsh()
                            }
                        )
                        if (!settings.keepSshActiveInBackground) {
                            Text(
                                "RECOMMENDED: enable this for a smoother experience when switching apps.",
                                color = MegaDrivePrimary,
                                fontFamily = MonoFontFamily,
                                fontSize = 11.sp
                            )
                        }
                        if (settings.keepSshActiveInBackground && !backgroundSshIsRunning) {
                            RetroButton(
                                text = "START ACTIVE NOTIFICATION",
                                onClick = ::requestBackgroundSshStart,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                "Android stopped the previous service. Your preference is saved, but this explicit tap is required to start it again.",
                                color = MegaDriveDim,
                                fontFamily = MonoFontFamily,
                                fontSize = 11.sp
                            )
                        }
                        SettingsSeparator()
                        SettingsToggleRow(
                            title = "SSH keepalive",
                            description = "Reduces silent disconnects while Android keeps TerminalHub running.",
                            status = if (settings.sshKeepaliveEnabled) "Enabled" else "Disabled",
                            checked = settings.sshKeepaliveEnabled,
                            onCheckedChange = viewModel::setSshKeepaliveEnabled
                        )
                        SettingsSeparator()
                        SettingsToggleRow(
                            title = "Fast resume",
                            description = "Restores terminal focus and redraw behavior quickly when returning to the app.",
                            status = if (settings.preferFastResume) "Enabled" else "Disabled",
                            checked = settings.preferFastResume,
                            onCheckedChange = viewModel::setPreferFastResume
                        )
                        SettingsSeparator()
                        SettingsSubheading(
                            title = "BATTERY OPTIMIZATION",
                            description = "Recommended for reliable SSH when the screen is off or you switch apps. Open Android settings, find TerminalHub, and choose Unrestricted or Don't optimize."
                        )
                        Text(
                            batteryOptimizationStatusLabel(batteryOptimizationExempt),
                            color = if (batteryOptimizationExempt) MegaDriveGreen else MegaDriveWarning,
                            fontFamily = MonoFontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        RetroButton(
                            text = if (batteryOptimizationExempt) {
                                "OPEN BATTERY SETTINGS"
                            } else {
                                "SET BATTERY TO UNRESTRICTED"
                            },
                            onClick = {
                                runCatching {
                                    batterySettingsLauncher.launch(
                                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    )
                                }.onFailure {
                                    Toast.makeText(context, "Battery settings are unavailable", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "Return here after changing it; the status refreshes automatically. Some phone brands also have a separate per-app battery setting that Android cannot report.",
                            color = MegaDriveDim,
                            fontFamily = MonoFontFamily,
                            fontSize = 11.sp
                        )
                        SettingsSeparator()
                        RetroButton(
                            text = "RECONNECT ALL",
                            onClick = onReconnectAll,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "Reconnects every disconnected SSH project tab in parallel.",
                            color = MegaDriveDim,
                            fontFamily = MonoFontFamily,
                            fontSize = 11.sp
                        )
                    }
                }
                item {
                    ExpandableSettingsSection(
                        title = "TERMINAL & INPUT",
                        summary = "Enter: ${if (settings.executeTextInputOnSend) "execute" else "text only"} · ${settings.keyBarRows.size} key bar rows",
                        importance = "EVERYDAY CONTROLS",
                        expanded = terminalInputExpanded,
                        onExpandedChange = { terminalInputExpanded = it }
                    ) {
                        SettingsToggleRow(
                            title = "Execute text input with Enter",
                            description = "Runs text immediately in the terminal. Disable it when composing or reviewing multiline commands.",
                            status = if (settings.executeTextInputOnSend) "Execute immediately" else "Send text only",
                            checked = settings.executeTextInputOnSend,
                            onCheckedChange = viewModel::setExecuteTextInputOnSend
                        )
                        SettingsSeparator()
                        SettingsSubheading(
                            title = "KEY BAR LAYOUT",
                            description = "Tap a key to replace or remove it. Add, delete or reorder up to ${se.joynes.terminalhub.data.settings.KeyBarLayoutConfig.MAX_ROWS} rows. Included in export/import."
                        )
                        Spacer(Modifier.height(10.dp))
                        KeyBarSettingsEditor(
                            rows = settings.keyBarRows,
                            onRowsChange = viewModel::setKeyBarRows
                        )
                    }
                }
                item {
                    ExpandableSettingsSection(
                        title = "ADVANCED CONNECTION TUNING",
                        summary = "${backgroundProfileLabel(settings.backgroundKeepaliveProfile)} · ${backgroundScopeLabel(settings.backgroundKeepaliveScope)}",
                        importance = "OPTIONAL",
                        expanded = advancedExpanded,
                        onExpandedChange = { advancedExpanded = it }
                    ) {
                        SettingsSubheading(
                            title = "KEEPALIVE PROFILE",
                            description = "How often opportunistic keepalives run while the app remains in memory."
                        )
                        SettingsValue("Current", backgroundProfileLabel(settings.backgroundKeepaliveProfile))
                        Spacer(Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            BackgroundKeepaliveProfile.entries.forEach { profile ->
                                RetroButton(
                                    text = backgroundProfileLabel(profile).uppercase(),
                                    onClick = { viewModel.setBackgroundKeepaliveProfile(profile) },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = settings.backgroundKeepaliveProfile != profile
                                )
                            }
                        }
                        SettingsSeparator()
                        SettingsSubheading(
                            title = "KEEPALIVE SCOPE",
                            description = "Active tab only uses the least background network traffic."
                        )
                        SettingsValue("Current", backgroundScopeLabel(settings.backgroundKeepaliveScope))
                        Spacer(Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            BackgroundKeepaliveScope.entries.forEach { scope ->
                                RetroButton(
                                    text = backgroundScopeLabel(scope).uppercase(),
                                    onClick = { viewModel.setBackgroundKeepaliveScope(scope) },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = settings.backgroundKeepaliveScope != scope
                                )
                            }
                        }
                    }
                }
                item {
                    ExpandableSettingsSection(
                        title = "STATUS & DIAGNOSTICS",
                        summary = "${if (runtimeState.appInForeground) "Foreground" else "Background"} · service ${if (runtimeState.foregroundServiceRunning) "running" else "stopped"}",
                        importance = "TROUBLESHOOTING",
                        expanded = statusExpanded,
                        onExpandedChange = { statusExpanded = it }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            }
        }
    }
}

@Composable
private fun QuickAccessCard(
    onOpenSessions: () -> Unit,
    onOpenServers: () -> Unit
) {
    RetroCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MegaDriveSurface)
                .padding(12.dp)
        ) {
            Text(
                "QUICK ACCESS",
                color = MegaDrivePrimary,
                fontFamily = MonoFontFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RetroButton(
                    text = "SESSIONS",
                    onClick = onOpenSessions,
                    modifier = Modifier.weight(1f)
                )
                RetroButton(
                    text = "SERVERS",
                    onClick = onOpenServers,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ExpandableSettingsSection(
    title: String,
    summary: String,
    importance: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    RetroCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MegaDriveSurface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandedChange(!expanded) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        importance,
                        color = MegaDriveDim,
                        fontFamily = MonoFontFamily,
                        fontSize = 10.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        title,
                        color = MegaDrivePrimary,
                        fontFamily = MonoFontFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        summary,
                        color = MegaDriveOnSurface,
                        fontFamily = MonoFontFamily,
                        fontSize = 11.sp
                    )
                }
                Text(
                    if (expanded) "[-]" else "[+]",
                    color = MegaDrivePrimary,
                    fontFamily = MonoFontFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            if (expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    description: String,
    status: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                title,
                color = MegaDriveOnSurface,
                fontFamily = MonoFontFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                description,
                color = MegaDriveDim,
                fontFamily = MonoFontFamily,
                fontSize = 11.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                status,
                color = MegaDrivePrimary,
                fontFamily = MonoFontFamily,
                fontSize = 11.sp
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsSubheading(title: String, description: String) {
    Column {
        Text(
            title,
            color = MegaDriveOnSurface,
            fontFamily = MonoFontFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            description,
            color = MegaDriveDim,
            fontFamily = MonoFontFamily,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun SettingsSeparator() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MegaDriveDim.copy(alpha = 0.3f))
    )
}

internal fun backgroundProfileLabel(profile: BackgroundKeepaliveProfile): String = when (profile) {
    BackgroundKeepaliveProfile.AGGRESSIVE -> "Aggressive (30 sec)"
    BackgroundKeepaliveProfile.BALANCED -> "Balanced (2 min)"
    BackgroundKeepaliveProfile.BATTERY_SAVER -> "Battery saver (5 min)"
    BackgroundKeepaliveProfile.ULTRA_BATTERY_SAVER -> "Ultra battery saver (10 min)"
}

internal fun backgroundScopeLabel(scope: BackgroundKeepaliveScope): String = when (scope) {
    BackgroundKeepaliveScope.ACTIVE_TAB_ONLY -> "Active tab only"
    BackgroundKeepaliveScope.ALL_SESSIONS -> "All SSH sessions"
}

@Composable
private fun SettingsValue(label: String, value: String) {
    Column {
        Text(label, color = MegaDriveDim, fontFamily = MonoFontFamily, fontSize = 11.sp)
        Spacer(Modifier.height(2.dp))
        Text(value, color = MegaDriveOnSurface, fontFamily = MonoFontFamily, fontSize = 12.sp)
    }
}
