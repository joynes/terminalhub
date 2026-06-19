package se.joynes.aiterminal.ui.screen.upload

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import se.joynes.aiterminal.ui.components.*
import se.joynes.aiterminal.ui.theme.*

/** Legacy full-screen upload screen — kept for navigation compatibility. */
@Composable
fun FileUploadScreen(
    serverId: Long,
    onBack: () -> Unit,
    viewModel: FileUploadViewModel = hiltViewModel()
) {
    Scaffold(
        topBar = { RetroTopBar(title = "FILE UPLOAD", onBack = onBack) },
        containerColor = MegaDriveBg
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MegaDriveBg)
                .padding(16.dp)
        ) {
            Text(
                "Use the + button in the terminal key bar to upload files.",
                color = MegaDriveDim,
                fontSize = 12.sp,
                fontFamily = MonoFontFamily
            )
        }
    }
}
