package se.joynes.aiterminal.ui.screen.download

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.joynes.aiterminal.data.ssh.RemoteFileEntry
import se.joynes.aiterminal.ui.components.PixelProgressBar
import se.joynes.aiterminal.ui.components.RetroButton
import se.joynes.aiterminal.ui.theme.MegaDriveAccent
import se.joynes.aiterminal.ui.theme.MegaDriveBg
import se.joynes.aiterminal.ui.theme.MegaDriveDim
import se.joynes.aiterminal.ui.theme.MegaDrivePrimary
import se.joynes.aiterminal.ui.theme.MegaDriveSurface
import se.joynes.aiterminal.ui.theme.MonoFontFamily
import kotlin.math.roundToInt

@Composable
fun FloatingFileDownloadDialog(
    viewModel: FileDownloadViewModel,
    projectId: Long,
    serverId: Long,
    downloadState: DownloadState,
    onDismiss: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val context = LocalContext.current

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val panelWidthDp = (configuration.screenWidthDp * 0.92f).dp
    val panelWidthPx = with(density) { panelWidthDp.toPx() }

    var offsetX by remember { mutableFloatStateOf(screenWidthPx * 0.04f) }
    var offsetY by remember { mutableFloatStateOf(with(density) { 80.dp.toPx() }) }
    var pendingFile by remember { mutableStateOf<RemoteFileEntry?>(null) }

    val destinationPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val file = pendingFile
        if (uri != null && file != null) {
            viewModel.startDownload(serverId, projectId, file.name, uri, context)
        }
        pendingFile = null
    }

    LaunchedEffect(Unit) {
        if (downloadState is DownloadState.Idle) {
            viewModel.loadRemoteFiles(serverId, projectId)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .width(panelWidthDp)
                .background(MegaDriveSurface, RoundedCornerShape(4.dp))
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .background(MegaDrivePrimary, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    .pointerInput(Unit) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            offsetX = (offsetX + drag.x).coerceIn(0f, screenWidthPx - panelWidthPx)
                            offsetY = (offsetY + drag.y).coerceIn(0f, with(density) { 600.dp.toPx() })
                        }
                    }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("REMOTE DOWNLOAD", color = MegaDriveBg, fontSize = 11.sp, fontFamily = MonoFontFamily)
                Text(
                    "x",
                    color = MegaDriveBg,
                    fontSize = 13.sp,
                    fontFamily = MonoFontFamily,
                    modifier = Modifier.clickable { viewModel.reset(projectId); onDismiss() }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (downloadState) {
                    is DownloadState.Idle,
                    is DownloadState.LoadingList -> {
                        Text("Loading remote files...", color = MegaDriveDim, fontSize = 11.sp, fontFamily = MonoFontFamily)
                    }
                    is DownloadState.Listed -> {
                        RemoteFileList(
                            files = downloadState.files,
                            onDownload = { file ->
                                pendingFile = file
                                destinationPicker.launch(file.name)
                            }
                        )
                        RetroButton(
                            text = "REFRESH",
                            onClick = { viewModel.loadRemoteFiles(serverId, projectId) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    is DownloadState.Downloading -> {
                        Text(downloadState.fileName, color = MegaDrivePrimary, fontSize = 11.sp, fontFamily = MonoFontFamily, maxLines = 2)
                        PixelProgressBar(
                            progress = downloadState.progress,
                            label = "DOWNLOADING...",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    is DownloadState.Done -> {
                        Text(
                            "DONE - ${downloadState.fileName}",
                            color = MegaDrivePrimary,
                            fontSize = 11.sp,
                            fontFamily = MonoFontFamily
                        )
                        Text(
                            formatBytes(downloadState.bytes),
                            color = MegaDriveDim,
                            fontSize = 10.sp,
                            fontFamily = MonoFontFamily
                        )
                        RetroButton(
                            text = "CLOSE",
                            onClick = { viewModel.reset(projectId); onDismiss() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    is DownloadState.Error -> {
                        Text(
                            "ERROR: ${downloadState.message}",
                            color = MegaDriveAccent,
                            fontSize = 11.sp,
                            fontFamily = MonoFontFamily
                        )
                        RetroButton(
                            text = "TRY AGAIN",
                            onClick = { viewModel.loadRemoteFiles(serverId, projectId) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoteFileList(
    files: List<RemoteFileEntry>,
    onDownload: (RemoteFileEntry) -> Unit
) {
    if (files.isEmpty()) {
        Text("No files in SSH start folder.", color = MegaDriveDim, fontSize = 11.sp, fontFamily = MonoFontFamily)
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 260.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        files.forEach { file ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MegaDriveBg, RoundedCornerShape(4.dp))
                    .clickable { onDownload(file) }
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    file.name,
                    color = MegaDrivePrimary,
                    fontSize = 11.sp,
                    fontFamily = MonoFontFamily,
                    maxLines = 2,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    formatBytes(file.size),
                    color = MegaDriveDim,
                    fontSize = 10.sp,
                    fontFamily = MonoFontFamily,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.1f GB".format(mb / 1024.0)
}
