package se.joynes.aiterminal.ui.screen.upload

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import se.joynes.aiterminal.ui.components.PixelProgressBar
import se.joynes.aiterminal.ui.components.RetroButton
import se.joynes.aiterminal.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun FloatingFileUploadDialog(
    viewModel: FileUploadViewModel,
    projectId: Long,
    serverId: Long,
    uploadState: UploadState,
    selectedUri: android.net.Uri?,
    selectedName: String,
    onSelectedUriChange: (android.net.Uri?) -> Unit,
    onSelectedNameChange: (String) -> Unit,
    initialUri: android.net.Uri? = null,
    onDismiss: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val screenWidthPx  = with(density) { configuration.screenWidthDp.dp.toPx() }
    val panelWidthDp   = (configuration.screenWidthDp * 0.92f).dp
    val panelWidthPx   = with(density) { panelWidthDp.toPx() }

    var offsetX by remember { mutableFloatStateOf(screenWidthPx * 0.04f) }
    var offsetY by remember { mutableFloatStateOf(with(density) { 80.dp.toPx() }) }

    // Multi-file queue: files waiting after the current one
    var uploadQueue by remember { mutableStateOf<List<Pair<android.net.Uri, String>>>(emptyList()) }
    var totalFiles by remember { mutableStateOf(0) }
    var fileIndex by remember { mutableStateOf(0) }  // 0-indexed current file

    fun resolveFileName(uri: android.net.Uri): String {
        var name = uri.lastPathSegment ?: "file"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) name = cursor.getString(idx) ?: name
            }
        }
        return name
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            val resolved = uris.map { it to resolveFileName(it) }
            totalFiles = resolved.size
            fileIndex = 0
            onSelectedUriChange(resolved.first().first)
            onSelectedNameChange(resolved.first().second)
            uploadQueue = resolved.drop(1)
        } else {
            if (selectedUri == null) onDismiss()
        }
    }

    // Auto-open picker when dialog appears
    LaunchedEffect(Unit) {
        if (selectedUri == null && initialUri == null) {
            filePicker.launch("*/*")
        }
    }

    // Pre-populate from share intent
    LaunchedEffect(initialUri) {
        if (initialUri != null && selectedUri == null) {
            onSelectedUriChange(initialUri)
            onSelectedNameChange(resolveFileName(initialUri))
            totalFiles = 1
            fileIndex = 0
        }
    }

    // Auto-start upload as soon as current URI is set
    LaunchedEffect(selectedUri) {
        val uri = selectedUri ?: return@LaunchedEffect
        if (uploadState !is UploadState.Uploading) {
            viewModel.startUpload(serverId, projectId, uri, context)
        }
    }

    // Advance to next file in queue when current finishes
    LaunchedEffect(uploadState) {
        if (uploadState is UploadState.Done && uploadQueue.isNotEmpty()) {
            val next = uploadQueue.first()
            uploadQueue = uploadQueue.drop(1)
            fileIndex += 1
            viewModel.reset(projectId)
            onSelectedUriChange(next.first)
            onSelectedNameChange(next.second)
        }
    }

    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            viewModel.reset(projectId)
            onDismiss()
        }
    }

    val allDone = uploadState is UploadState.Done && uploadQueue.isEmpty() && totalFiles > 0
    val fileLabel = if (totalFiles > 1) " (${fileIndex + 1}/$totalFiles)" else ""

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
    ) {
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
            // Draggable title bar
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
                Text("FILE UPLOAD", color = MegaDriveBg, fontSize = 11.sp, fontFamily = MonoFontFamily)
                Text(
                    "✕", color = MegaDriveBg, fontSize = 13.sp, fontFamily = MonoFontFamily,
                    modifier = Modifier.clickable { viewModel.reset(projectId); onDismiss() }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val isUploading = uploadState is UploadState.Uploading
                val isDone      = uploadState is UploadState.Done
                val isError     = uploadState is UploadState.Error

                if (selectedName.isNotBlank()) {
                    Text(
                        text = selectedName,
                        color = MegaDrivePrimary,
                        fontSize = 11.sp,
                        fontFamily = MonoFontFamily,
                        maxLines = 2
                    )
                } else if (!isUploading && !isDone && !isError) {
                    Text("Selecting file...", color = MegaDriveDim, fontSize = 11.sp, fontFamily = MonoFontFamily)
                }

                when {
                    isUploading -> {
                        val state = uploadState as UploadState.Uploading
                        PixelProgressBar(
                            progress = state.progress,
                            label = "UPLOADING$fileLabel...",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    allDone -> {
                        val doneState = uploadState as UploadState.Done
                        if (totalFiles > 1) {
                            Text(
                                "DONE ✓  $totalFiles files uploaded",
                                color = MegaDrivePrimary,
                                fontSize = 11.sp,
                                fontFamily = MonoFontFamily
                            )
                            RetroButton(
                                text = "CLOSE",
                                onClick = { viewModel.reset(projectId); onDismiss() },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(
                                if (copied) "COPIED ✓" else "DONE ✓  ${doneState.fileName}",
                                color = MegaDrivePrimary,
                                fontSize = 11.sp,
                                fontFamily = MonoFontFamily
                            )
                            Text(
                                doneState.remotePath,
                                color = MegaDriveDim,
                                fontSize = 10.sp,
                                fontFamily = MonoFontFamily,
                                maxLines = 2
                            )
                            RetroButton(
                                text = if (copied) "COPIED!" else "COPY PATH",
                                onClick = {
                                    clipboard.setText(AnnotatedString(doneState.remotePath))
                                    copied = true
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    isError -> {
                        Text(
                            "ERROR: ${(uploadState as UploadState.Error).message}",
                            color = MegaDriveAccent,
                            fontSize = 11.sp,
                            fontFamily = MonoFontFamily
                        )
                        RetroButton(
                            text = "CHOOSE FILES",
                            onClick = { filePicker.launch("*/*") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Re-pick only when idle with file already chosen
                if (!isDone && !isUploading && !isError && selectedUri != null) {
                    RetroButton(
                        text = "CHOOSE DIFFERENT FILES",
                        onClick = { uploadQueue = emptyList(); filePicker.launch("*/*") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
