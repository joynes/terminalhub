package se.joynes.aiterminal.ui.screen.status

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import se.joynes.aiterminal.data.model.ServerStatus
import se.joynes.aiterminal.ui.components.*
import se.joynes.aiterminal.ui.theme.*

@Composable
fun ServerStatusScreen(
    serverId: Long,
    onBack: () -> Unit,
    viewModel: ServerStatusViewModel = hiltViewModel()
) {
    val status by viewModel.status.collectAsState()
    LaunchedEffect(serverId) { viewModel.startPolling(serverId) }

    Scaffold(
        topBar = { RetroTopBar(title = "SERVER STATUS", onBack = onBack) },
        containerColor = MegaDriveBg
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MegaDriveBg)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("SYSTEM METRICS", color = MegaDrivePrimary, fontSize = 14.sp, fontFamily = MonoFontFamily)
            StatusGauge("CPU", status?.cpuPercent ?: 0f, MegaDrivePrimary)
            StatusGauge("RAM", status?.ramPercent ?: 0f, MegaDriveAccent)
            StatusGauge("DISK", status?.diskPercent ?: 0f, MegaDriveWarning)
        }
    }
}

@Composable
private fun StatusGauge(label: String, percent: Float, color: androidx.compose.ui.graphics.Color) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = color, fontSize = 12.sp, fontFamily = MonoFontFamily)
            Text("${percent.toInt()}%", color = color, fontSize = 12.sp, fontFamily = MonoFontFamily)
        }
        Spacer(Modifier.height(4.dp))
        PixelProgressBar(progress = percent / 100f)
    }
}
