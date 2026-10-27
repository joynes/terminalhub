package se.joynes.terminalhub

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.joynes.terminalhub.ui.screen.download.*
import se.joynes.terminalhub.ui.theme.TerminalHubTheme
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class RemoteImagePreviewTest {
    @get:Rule val compose = createComposeRule()

    private fun png(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }

    @Test fun decoderLoadsImagesAndBoundsLargeDimensions() {
        val bitmap = decodePreviewImage(png(4096, 512))!!
        assertTrue(bitmap.width <= 2048)
        assertTrue(bitmap.height <= 2048)
        assertEquals(8, bitmap.width / bitmap.height)
        bitmap.recycle()
    }

    @Test fun decoderRejectsBrokenAndOversizedImages() {
        assertNull(decodePreviewImage(byteArrayOf(1, 2, 3)))
        assertNull(decodePreviewImage(ByteArray(MAX_REMOTE_IMAGE_PREVIEW_BYTES.toInt() + 1)))
    }

    @Test fun imageCanBePreviewedWithoutSelectingForDownload() {
        val entry = se.joynes.terminalhub.data.ssh.RemoteFileEntry("photo.png", 100)
        var previewed = false
        var selected = false
        compose.setContent {
            TerminalHubTheme {
                RemoteFileList(listOf(entry), emptySet(), onOpenDirectory = {},
                    onToggleFile = { selected = true }, onPreviewFile = { previewed = true })
            }
        }
        compose.onNodeWithText("PREVIEW").performClick()
        compose.runOnIdle { assertTrue(previewed); assertFalse(selected) }
    }

    @Test fun imageDialogDisplaysAndCloses() {
        val bytes = png(32, 32)
        var closed = false
        compose.setContent {
            TerminalHubTheme { RemoteImagePreviewDialog("photo.png", bytes) { closed = true } }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("Preview of photo.png")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Preview of photo.png").assertExists()
        compose.onNodeWithText("RESET ZOOM").performClick()
        compose.onNodeWithContentDescription("Close file browser").performClick()
        compose.runOnIdle { assertTrue(closed) }
    }

    @Test fun corruptedImageShowsReadableErrorAndCanBeClosed() {
        compose.setContent {
            TerminalHubTheme { RemoteImagePreviewDialog("broken.png", byteArrayOf(1, 2)) {} }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasText("Cannot preview this image.", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Close file browser").assertExists()
    }
}
