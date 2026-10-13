package se.joynes.terminalhub.ui.screen.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.hilt.navigation.compose.hiltViewModel
import se.joynes.terminalhub.data.settings.SettingsImportPreview
import se.joynes.terminalhub.ui.screen.export.*

@Composable
internal fun SettingsTransferSection(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val backup: ExportImportViewModel = hiltViewModel()
    val backupState by backup.state.collectAsState()
    val preview by viewModel.settingsPreview.collectAsState()
    val busy by viewModel.settingsFileBusy.collectAsState()
    val message by viewModel.settingsFileMessage.collectAsState()
    var includeNotes by rememberSaveable { mutableStateOf(false) }
    var backupOptions by rememberSaveable { mutableStateOf(false) }
    var importBackupWarning by rememberSaveable { mutableStateOf(false) }
    val exportSettings = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/yaml")) {
        it?.let(viewModel::exportSettings)
    }
    val importSettings = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let(viewModel::previewSettings)
    }
    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/yaml")) {
        it?.let { uri -> backup.export(context, uri, activeProjectIds = null, includeLocalNotes = includeNotes) }
    }
    val importBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let { uri -> backup.import(context, uri) }
    }
    LaunchedEffect(message) {
        message?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); viewModel.settingsFileMessage.value = null }
    }
    LaunchedEffect(backupState) {
        val status = when (val result = backupState) {
            ExportImportState.ExportDone -> "Full app configuration exported"
            is ExportImportState.ImportDone -> "Restored ${result.result.servers} servers and ${result.result.projects} projects. Re-enter SSH credentials."
            is ExportImportState.Error -> result.message
            else -> null
        }
        status?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); backup.resetState() }
    }
    val working = busy || backupState == ExportImportState.Working
    if (working) LinearProgressIndicator(Modifier.fillMaxWidth())
    SettingsTransferChoices(working,
        onExportSettings = { exportSettings.launch("terminalhub-settings.yaml") },
        onImportSettings = { importSettings.launch(arrayOf("*/*")) },
        onExportBackup = { includeNotes = false; backupOptions = true },
        onImportBackup = { importBackupWarning = true })
    preview?.let { item -> SettingsImportPreviewDialog(item,
        onCancel = { viewModel.settingsPreview.value = null }, onApply = viewModel::applySettingsPreview) }
    if (backupOptions) AlertDialog(
        onDismissRequest = { backupOptions = false }, title = { Text("Export full app configuration") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("Includes ALL saved servers and projects, settings and pinned actions — not just open tabs. Passwords, private SSH keys, terminal history and downloaded/project files are not included.")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(includeNotes, { includeNotes = it }); Text("Include local project notes")
            }
            Text("Local notes need a backup before uninstalling. Included notes and pinned commands are readable private text. SSH notes stay on the server and return when you restore the same server/user/project path. Protect this backup.")
        } }, confirmButton = { TextButton(onClick = { backupOptions = false; exportBackup.launch("terminalhub-configuration.yaml") }) { Text("EXPORT BACKUP") } },
        dismissButton = { TextButton(onClick = { backupOptions = false }) { Text("CANCEL") } })
    if (importBackupWarning) AlertDialog(
        onDismissRequest = { importBackupWarning = false }, title = { Text("Replace app configuration?") },
        text = { Text("Full backup import replaces current servers, projects, pins and optional local notes, closes sessions and clears stored SSH credentials/history. Re-enter passwords or SSH keys afterwards. History and background SSH approval must be enabled again. This is NOT the settings-only importer.") },
        confirmButton = { TextButton(onClick = { importBackupWarning = false; importBackup.launch(arrayOf("*/*")) }) { Text("CHOOSE BACKUP") } },
        dismissButton = { TextButton(onClick = { importBackupWarning = false }) { Text("CANCEL") } })
}

@Composable
internal fun SettingsTransferChoices(busy: Boolean, onExportSettings: () -> Unit, onImportSettings: () -> Unit,
    onExportBackup: () -> Unit, onImportBackup: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("1 · AI SETTINGS — PREFERENCES ONLY", style = MaterialTheme.typography.titleSmall)
        Text("Commented YAML explaining every setting, defaults, ranges and trade-offs. Discuss it with an AI and import the edited result. Preview and approve changes; servers, projects, pins and notes stay untouched.")
        Row { TextButton(onExportSettings, enabled = !busy) { Text("EXPORT SETTINGS") }; TextButton(onImportSettings, enabled = !busy) { Text("IMPORT SETTINGS") } }
        HorizontalDivider()
        Text("2 · FULL APP CONFIGURATION — NEW PHONE / BACKUP", style = MaterialTheme.typography.titleSmall)
        Text("All saved servers/projects, preferences and pins, with optional local notes. Import REPLACES the current setup. SSH credentials, terminal history, remote notes and project files are not included. Not intended as the AI-editing file.")
        Row { TextButton(onExportBackup, enabled = !busy) { Text("EXPORT BACKUP") }; TextButton(onImportBackup, enabled = !busy) { Text("RESTORE BACKUP") } }
    }
}

@Composable
internal fun SettingsImportPreviewDialog(preview: SettingsImportPreview, onCancel: () -> Unit, onApply: (Boolean) -> Unit) {
    var approved by remember(preview) { mutableStateOf(false) }
    val sensitive = preview.enablesHistory || preview.enablesBackground
    AlertDialog(onDismissRequest = onCancel, title = { Text("Review ${preview.changes.size} setting changes") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Settings only. Servers, projects, pins, notes and credentials are not replaced.")
            if (preview.changes.isEmpty()) Text("No settings differ from this device.")
            preview.changes.forEach { Text("${it.key}\n${it.before} → ${it.after}", style = MaterialTheme.typography.bodySmall) }
            if (preview.enablesHistory) Text("PRIVACY: terminal history can store passwords, API keys, tokens and private commands locally. Recording is per project; this enables it for all projects on this device.")
            if (preview.enablesBackground) Text("BACKGROUND SSH: an ongoing notification and extra battery/mobile data may be used. Automatic start can occur when you return to a visible connected terminal. Android notification permission is still required.")
            if (preview.base.keepSshActiveInBackground && !preview.next.keepSshActiveInBackground) Text("Turning off background SSH can disconnect remote tabs; remote tmux work continues.")
            if (sensitive) Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(approved, { approved = it }); Text("I understand and approve the privacy/background changes")
            }
        } },
        confirmButton = { TextButton(onClick = { onApply(approved) }, enabled = preview.changes.isNotEmpty() && (!sensitive || approved)) { Text("APPLY SETTINGS") } },
        dismissButton = { TextButton(onCancel) { Text("CANCEL") } })
}
