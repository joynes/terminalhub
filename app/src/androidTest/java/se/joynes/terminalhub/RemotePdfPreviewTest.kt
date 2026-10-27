package se.joynes.terminalhub

import android.graphics.pdf.PdfDocument
import android.graphics.Paint
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import se.joynes.terminalhub.ui.screen.download.*
import se.joynes.terminalhub.ui.theme.TerminalHubTheme
import java.io.ByteArrayOutputStream

class RemotePdfPreviewTest {
    @get:Rule val compose = createComposeRule()
    private fun document(): ByteArray {
        val pdf = PdfDocument()
        try {
        repeat(3) { index ->
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(400, 500, index + 1).create())
            page.canvas.drawText("Test page ${index + 1}", 30f, 40f, Paint().apply { textSize = 20f })
            pdf.finishPage(page)
        }
        return ByteArrayOutputStream().also { pdf.writeTo(it) }.toByteArray()
        } finally { pdf.close() }
    }
    @Test fun nativeRendererRendersPagesAndDeletesPrivateTemporaryFile() {
        val cache = ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir
        val before = cache.list()!!.toSet()
        RemotePdfDocument(cache, document()).use { pdf ->
            assertEquals(3, pdf.pageCount)
            val bitmap = pdf.render(2)
            assertTrue(bitmap.width > 0 && bitmap.height <= 2000)
            bitmap.recycle()
        }
        assertEquals(before, cache.list()!!.toSet())
        assertTrue(runCatching { RemotePdfDocument(cache, byteArrayOf(1, 2, 3)) }.isFailure)
        assertEquals(before, cache.list()!!.toSet())
    }
    @Test fun pdfPagesScrollZoomAndCloseWithoutDownloadPicker() {
        var closed = false
        val bytes = document()
        compose.setContent { TerminalHubTheme { RemotePdfPreviewDialog("report.pdf", bytes) { closed = true } } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("remote-pdf-preview").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("remote-pdf-preview").performScrollToNode(hasText("Page 3"))
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("PDF page 3").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("+").performClick()
        compose.onNodeWithText("150%").assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle { assertTrue(closed) }
    }
}
