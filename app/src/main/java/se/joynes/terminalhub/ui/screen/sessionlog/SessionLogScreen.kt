package se.joynes.terminalhub.ui.screen.sessionlog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import se.joynes.terminalhub.data.db.entity.SessionLogEntity
import se.joynes.terminalhub.ui.components.*
import se.joynes.terminalhub.ui.theme.*
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast

@Composable
fun SessionLogScreen(
    onBack: () -> Unit,
    viewModel: SessionLogViewModel = hiltViewModel()
) {
    val logs by viewModel.logs.collectAsState()
    val context = LocalContext.current
    val exporting by viewModel.exporting.collectAsState()
    val exportMessage by viewModel.exportMessage.collectAsState()
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri?.let { viewModel.export(context, it) }
    }
    LaunchedEffect(exportMessage) {
        exportMessage?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); viewModel.exportMessage.value = null }
    }

    Scaffold(
        topBar = {
            RetroTopBar(
                title = "SESSION LOG",
                onBack = onBack,
                actions = {
                    IconButton(enabled = !exporting, onClick = { export.launch("terminalhub-session-log.txt") }) {
                        Text("EXP", color = MegaDriveWarning, fontSize = 10.sp, fontFamily = MonoFontFamily)
                    }
                }
            )
        },
        containerColor = MegaDriveBg
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).background(MegaDriveBg),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (logs.isEmpty()) {
                item {
                    Text("NO SESSION LOGS", color = MegaDriveDim, fontSize = 12.sp, fontFamily = MonoFontFamily)
                }
            } else {
                items(logs) { log ->
                    RetroCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("Session: ${log.sessionId.take(8)}...", color = MegaDrivePrimary, fontSize = 12.sp, fontFamily = MonoFontFamily)
                            Text("Started: ${log.startedAt}", color = MegaDriveOnSurface, fontSize = 10.sp, fontFamily = MonoFontFamily)
                            if (log.endedAt != null) {
                                Text("Ended: ${log.endedAt}", color = MegaDriveDim, fontSize = 10.sp, fontFamily = MonoFontFamily)
                            }
                        }
                    }
                }
            }
        }
    }
}
