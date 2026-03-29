package se.joynes.aiterminalhub.ui.screen.upload

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
import se.joynes.aiterminalhub.ui.components.PixelProgressBar
import se.joynes.aiterminalhub.ui.components.RetroButton
import se.joynes.aiterminalhub.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun FloatingFileUploadDialog(
    viewModel: FileUploadViewModel,
    projectId: Long,
    serverId: Long,
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

    var selectedUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var selectedName by remember { mutableStateOf("") }

    // Pre-populate from share intent
    LaunchedEffect(initialUri) {
        if (initialUri != null && selectedUri == null) {
            selectedUri = initialUri
            context.contentResolver.query(initialUri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) selectedName = cursor.getString(idx) ?: initialUri.lastPathSegment ?: "file"
                }
            }
            if (selectedName.isBlank()) selectedName = initialUri.lastPathSegment ?: "file"
        }
    }

    val uploadState by viewModel.uploadState.collectAsState()

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedUri = uri
            // Resolve display name immediately for the label
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) selectedName = cursor.getString(idx) ?: uri.lastPathSegment ?: "file"
                }
            }
            if (selectedName.isBlank()) selectedName = uri.lastPathSegment ?: "file"
        }
    }

    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            viewModel.reset()
            onDismiss()
        }
    }

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
                    modifier = Modifier.clickable { viewModel.reset(); onDismiss() }
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

                RetroButton(
                    text = "CHOOSE FILE",
                    onClick = { if (!isUploading) filePicker.launch("*/*") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (selectedName.isNotBlank()) {
                    Text(
                        text = selectedName,
                        color = MegaDrivePrimary,
                        fontSize = 11.sp,
                        fontFamily = MonoFontFamily,
                        maxLines = 2
                    )
                }

                when {
                    isUploading -> {
                        val state = uploadState as UploadState.Uploading
                        PixelProgressBar(
                            progress = state.progress,
                            label = "UPLOADING...",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    isDone -> {
                        val doneState = uploadState as UploadState.Done
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
                    isError -> {
                        Text(
                            "ERROR: ${(uploadState as UploadState.Error).message}",
                            color = MegaDriveAccent,
                            fontSize = 11.sp,
                            fontFamily = MonoFontFamily
                        )
                    }
                }

                if (!isDone && !isUploading) {
                    RetroButton(
                        text = "UPLOAD",
                        onClick = {
                            val uri = selectedUri ?: return@RetroButton
                            viewModel.startUpload(serverId, projectId, uri, context)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selectedUri != null
                    )
                }
            }
        }
    }
}
