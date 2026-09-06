package se.joynes.terminalhub.ui.screen.download

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import se.joynes.terminalhub.data.ssh.RemoteFileEntry
import se.joynes.terminalhub.ui.components.PixelProgressBar
import se.joynes.terminalhub.ui.components.RetroButton
import se.joynes.terminalhub.ui.theme.MegaDriveAccent
import se.joynes.terminalhub.ui.theme.MegaDriveBg
import se.joynes.terminalhub.ui.theme.MegaDriveDim
import se.joynes.terminalhub.ui.theme.MegaDrivePrimary
import se.joynes.terminalhub.ui.theme.MegaDriveSurface
import se.joynes.terminalhub.ui.theme.MonoFontFamily
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
    var pendingFile by remember { mutableStateOf<PendingRemoteDownload?>(null) }

    val destinationPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val pending = pendingFile
        if (uri != null && pending != null) {
            viewModel.startDownload(
                serverId = serverId,
                projectId = projectId,
                relativeDirectory = pending.directory,
                fileName = pending.file.name,
                uri = uri,
                context = context
            )
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
                        Text(
                            remoteDirectoryLabel(downloadState.directory),
                            color = MegaDriveDim,
                            fontSize = 10.sp,
                            fontFamily = MonoFontFamily,
                            maxLines = 2
                        )
                        RemoteFileList(
                            entries = downloadState.entries,
                            onOpenDirectory = { directory ->
                                viewModel.loadRemoteFiles(
                                    serverId,
                                    projectId,
                                    childRemoteDirectory(downloadState.directory, directory.name)
                                )
                            },
                            onDownload = { file ->
                                pendingFile = PendingRemoteDownload(downloadState.directory, file)
                                destinationPicker.launch(file.name)
                            }
                        )
                        if (downloadState.directory.isNotEmpty()) {
                            RetroButton(
                                text = "UP ONE LEVEL",
                                onClick = {
                                    viewModel.loadRemoteFiles(
                                        serverId,
                                        projectId,
                                        parentRemoteDirectory(downloadState.directory)
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        RetroButton(
                            text = "REFRESH",
                            onClick = {
                                viewModel.loadRemoteFiles(serverId, projectId, downloadState.directory)
                            },
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
                            text = "OPEN",
                            onClick = { openDownloadedFile(context, downloadState) },
                            modifier = Modifier.fillMaxWidth()
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
                            onClick = {
                                viewModel.loadRemoteFiles(serverId, projectId, downloadState.directory)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

private fun openDownloadedFile(context: Context, download: DownloadState.Done) {
    val reportedType = context.contentResolver.getType(download.uri)
    val mimeType = reportedType
        ?.takeUnless { it.equals("application/octet-stream", ignoreCase = true) }
        ?: downloadedFileMimeType(download.fileName)
    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(download.uri, mimeType)
        clipData = ClipData.newUri(context.contentResolver, download.fileName, download.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(viewIntent, "Open ${download.fileName}").apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    runCatching { context.startActivity(chooser) }
        .onFailure {
            Toast.makeText(context, "No app can open this file", Toast.LENGTH_LONG).show()
        }
}

internal fun downloadedFileMimeType(fileName: String): String {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    return when (extension) {
        "aac" -> "audio/aac"
        "flac" -> "audio/flac"
        "m4a" -> "audio/mp4"
        "mid", "midi" -> "audio/midi"
        "mp3" -> "audio/mpeg"
        "ogg", "oga" -> "audio/ogg"
        "wav" -> "audio/wav"
        "webm" -> "audio/webm"
        "avi" -> "video/x-msvideo"
        "m4v", "mp4" -> "video/mp4"
        "mov" -> "video/quicktime"
        "gif" -> "image/gif"
        "jpeg", "jpg" -> "image/jpeg"
        "png" -> "image/png"
        "svg" -> "image/svg+xml"
        "webp" -> "image/webp"
        "csv" -> "text/csv"
        "htm", "html" -> "text/html"
        "json" -> "application/json"
        "log", "md", "sh", "txt" -> "text/plain"
        "pdf" -> "application/pdf"
        "zip" -> "application/zip"
        else -> "*/*"
    }
}

@Composable
private fun RemoteFileList(
    entries: List<RemoteFileEntry>,
    onOpenDirectory: (RemoteFileEntry) -> Unit,
    onDownload: (RemoteFileEntry) -> Unit
) {
    if (entries.isEmpty()) {
        Text("This remote folder is empty.", color = MegaDriveDim, fontSize = 11.sp, fontFamily = MonoFontFamily)
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 260.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        entries.forEach { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MegaDriveBg, RoundedCornerShape(4.dp))
                    .clickable {
                        if (entry.isDirectory) onOpenDirectory(entry) else onDownload(entry)
                    }
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    if (entry.isDirectory) "[DIR]  ${entry.name}" else entry.name,
                    color = MegaDrivePrimary,
                    fontSize = 11.sp,
                    fontFamily = MonoFontFamily,
                    maxLines = 2,
                    modifier = Modifier.weight(1f)
                )
                if (entry.isDirectory) {
                    Text(">", color = MegaDrivePrimary, fontSize = 11.sp, fontFamily = MonoFontFamily)
                } else {
                    Text(
                        formatBytes(entry.size),
                        color = MegaDriveDim,
                        fontSize = 10.sp,
                        fontFamily = MonoFontFamily,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

private data class PendingRemoteDownload(val directory: String, val file: RemoteFileEntry)

internal fun childRemoteDirectory(parent: String, childName: String): String {
    require(childName.isNotBlank() && '/' !in childName && childName != "." && childName != "..") {
        "Invalid remote directory"
    }
    return listOf(parent.trim('/'), childName).filter { it.isNotEmpty() }.joinToString("/")
}

internal fun parentRemoteDirectory(directory: String): String = directory.substringBeforeLast('/', "")

internal fun remoteDirectoryLabel(directory: String): String =
    if (directory.isEmpty()) "PROJECT /" else "PROJECT / $directory"

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.1f GB".format(mb / 1024.0)
}
