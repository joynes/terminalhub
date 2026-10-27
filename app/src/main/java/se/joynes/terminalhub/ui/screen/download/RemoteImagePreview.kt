package se.joynes.terminalhub.ui.screen.download

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import se.joynes.terminalhub.ui.components.RetroButton
import se.joynes.terminalhub.ui.theme.*

internal const val MAX_REMOTE_IMAGE_PREVIEW_BYTES = 8L * 1024 * 1024

// Native raster formats only. SVG is active markup and is deliberately not rendered.
internal fun isPreviewableImageFile(name: String): Boolean =
    name.substringAfterLast('.', "").lowercase() in setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")

internal fun decodePreviewImage(bytes: ByteArray): Bitmap? {
    if (bytes.size > MAX_REMOTE_IMAGE_PREVIEW_BYTES) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
        inSampleSize = sample
    })
}

@Composable
internal fun RemoteImagePreviewDialog(fileName: String, bytes: ByteArray, onDismiss: () -> Unit) {
    val result by produceState<Result<Bitmap?>?>(null, bytes) {
        value = withContext(Dispatchers.IO) { runCatching { decodePreviewImage(bytes) } }
    }
    var scale by remember(bytes) { mutableFloatStateOf(1f) }
    var offset by remember(bytes) { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    RemoteBrowserFrame(fileName, onDismiss) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Text("Pinch to zoom · drag to move · GIF shows a still frame", color = MegaDriveDim)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RetroButton(text = "RESET ZOOM", onClick = { scale = 1f; offset = androidx.compose.ui.geometry.Offset.Zero })
            }
            val bitmap = result?.getOrNull()
            if (bitmap != null) {
                Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().pointerInput(bytes) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 8f)
                        offset = if (scale == 1f) androidx.compose.ui.geometry.Offset.Zero else offset + pan
                    }
                }) {
                    Image(bitmap.asImageBitmap(), contentDescription = "Preview of $fileName",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().testTag("remote-image-preview").graphicsLayer {
                            scaleX = scale; scaleY = scale
                            translationX = offset.x; translationY = offset.y
                        })
                }
            } else Text(if (result == null) "Loading image…" else "Cannot preview this image. It may be damaged or unsupported.", color = MegaDrivePrimary)
        }
    }
}
