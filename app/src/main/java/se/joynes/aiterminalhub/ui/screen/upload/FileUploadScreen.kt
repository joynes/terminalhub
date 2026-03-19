package se.joynes.aiterminalhub.ui.screen.upload

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import se.joynes.aiterminalhub.ui.components.*
import se.joynes.aiterminalhub.ui.theme.*

@Composable
fun FileUploadScreen(
    serverId: Long,
    onBack: () -> Unit,
    viewModel: FileUploadViewModel = hiltViewModel()
) {
    val uploads by viewModel.uploads.collectAsState()
    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        viewModel.addFiles(uris, context)
    }

    Scaffold(
        topBar = { RetroTopBar(title = "FILE UPLOAD", onBack = onBack) },
        containerColor = MegaDriveBg
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MegaDriveBg)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RetroButton("[ SELECT FILES ]", { filePicker.launch("*/*") }, Modifier.fillMaxWidth())

            if (uploads.isNotEmpty()) {
                Text("SELECTED FILES:", color = MegaDrivePrimary, fontSize = 12.sp, fontFamily = MonoFontFamily)
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uploads) { upload ->
                        RetroCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(upload.name, color = MegaDriveOnSurface, fontSize = 12.sp, fontFamily = MonoFontFamily)
                                PixelProgressBar(progress = upload.progress, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                            }
                        }
                    }
                }
                RetroButton(
                    text = "[ UPLOAD ]",
                    onClick = { viewModel.startUpload(serverId) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
