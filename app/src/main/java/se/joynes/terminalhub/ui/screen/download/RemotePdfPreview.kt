package se.joynes.terminalhub.ui.screen.download

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import java.io.Closeable
import java.io.File
import se.joynes.terminalhub.ui.theme.*

internal const val MAX_REMOTE_PDF_PREVIEW_BYTES = 16L * 1024 * 1024
internal fun isPreviewablePdfFile(name: String) = name.substringAfterLast('.', "").equals("pdf", true)
internal fun isRemotePreviewSupported(name: String) =
    isPreviewableTextFile(name) || isPreviewableImageFile(name) || isPreviewablePdfFile(name)

/** PdfRenderer requires a seekable file. Keep it private and remove it when the viewer closes. */
internal class RemotePdfDocument(cache: File, bytes: ByteArray) : Closeable {
    private val file = File.createTempFile("remote-preview-", ".pdf", cache)
    private var renderer: PdfRenderer? = null
    val pageCount: Int
    init {
        try {
            require(bytes.size <= MAX_REMOTE_PDF_PREVIEW_BYTES)
            file.writeBytes(bytes)
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = try { PdfRenderer(descriptor) } catch (error: Exception) { descriptor.close(); throw error }
            pageCount = renderer!!.pageCount
            require(pageCount in 1..2000) { "This PDF has too many pages to preview." }
        } catch (error: Exception) { close(); throw error }
    }
    @Synchronized fun render(index: Int): Bitmap {
        val document = checkNotNull(renderer) { "Preview closed" }
        return document.openPage(index).use { page ->
            val scale = minOf(1400f / page.width, 2000f / page.height)
            val bitmap = Bitmap.createBitmap((page.width * scale).toInt().coerceAtLeast(1),
                (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            } catch (error: Exception) { bitmap.recycle(); throw error }
        }
    }
    @Synchronized override fun close() {
        try { renderer?.close() } finally { renderer = null; file.delete() }
    }
}

@Composable
internal fun RemotePdfPreviewDialog(fileName: String, bytes: ByteArray, onDismiss: () -> Unit) {
    val cache = LocalContext.current.cacheDir
    val result by produceState<Result<RemotePdfDocument>?>(null, bytes) {
        var opened: RemotePdfDocument? = null
        try {
            val loaded = withContext(Dispatchers.IO) { runCatching { RemotePdfDocument(cache, bytes).also { opened = it } } }
            value = loaded
            awaitCancellation()
        } finally { withContext(NonCancellable + Dispatchers.IO) { opened?.close() } }
    }
    var zoom by remember(bytes) { mutableFloatStateOf(1f) }
    RemoteBrowserFrame(fileName, onDismiss) {
        val document = result?.getOrNull()
        if (document == null) {
            Text(if (result == null) "Opening PDF…" else "Cannot preview this PDF. It may be encrypted, damaged or unsupported.",
                modifier = Modifier.padding(16.dp), color = MegaDriveOnSurface)
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${document.pageCount} pages", color = MegaDriveDim)
                TextButton(onClick = { zoom = (zoom - .5f).coerceAtLeast(1f) }, enabled = zoom > 1f) { Text("−") }
                TextButton(onClick = { zoom = 1f }) { Text("${(zoom * 100).toInt()}%") }
                TextButton(onClick = { zoom = (zoom + .5f).coerceAtMost(3f) }, enabled = zoom < 3f) { Text("+") }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("remote-pdf-preview"),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items((0 until document.pageCount).toList(), key = { it }) { index ->
                    val page by produceState<Result<Bitmap>?>(null, document, index) {
                        value = withContext(Dispatchers.IO) { runCatching { document.render(index) } }
                    }
                    Column {
                        Text("Page ${index + 1}", modifier = Modifier.padding(horizontal = 12.dp), color = MegaDriveDim)
                        val bitmap = page?.getOrNull()
                        if (bitmap != null) BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val width = maxWidth * zoom
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                                Image(bitmap.asImageBitmap(), "PDF page ${index + 1}",
                                    modifier = Modifier.width(width).height(width * bitmap.height.toFloat() / bitmap.width))
                            }
                        } else Text(if (page == null) "Loading page…" else "Cannot render this page.",
                            modifier = Modifier.padding(24.dp), color = MegaDriveOnSurface)
                    }
                }
            }
        }
    }
}
