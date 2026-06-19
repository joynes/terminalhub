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

@Composable
fun SessionLogScreen(
    onBack: () -> Unit,
    viewModel: SessionLogViewModel = hiltViewModel()
) {
    val logs by viewModel.logs.collectAsState()

    Scaffold(
        topBar = {
            RetroTopBar(
                title = "SESSION LOG",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { viewModel.export() }) {
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
