package se.joynes.terminalhub.ui.screen.download

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadMimeTypesTest {
    @Test fun downloadedMediaAndDocumentsUseInteroperableMimeTypesIncludingUppercaseExtensions() {
        val formats = mapOf("aac" to "audio/aac", "flac" to "audio/flac", "m4a" to "audio/mp4", "mid" to "audio/midi",
            "midi" to "audio/midi", "mp3" to "audio/mpeg", "ogg" to "audio/ogg", "oga" to "audio/ogg", "wav" to "audio/wav",
            "webm" to "audio/webm", "avi" to "video/x-msvideo", "m4v" to "video/mp4", "mp4" to "video/mp4", "mov" to "video/quicktime",
            "gif" to "image/gif", "jpeg" to "image/jpeg", "jpg" to "image/jpeg", "png" to "image/png", "svg" to "image/svg+xml",
            "webp" to "image/webp", "csv" to "text/csv", "htm" to "text/html", "html" to "text/html", "json" to "application/json",
            "log" to "text/plain", "md" to "text/plain", "sh" to "text/plain", "txt" to "text/plain", "pdf" to "application/pdf", "zip" to "application/zip")
        formats.forEach { (extension, mime) ->
            assertEquals("File .$extension", mime, downloadedFileMimeType("my.document.$extension"))
            assertEquals(mime, downloadedFileMimeType("my.document.${extension.uppercase()}"))
        }
        assertEquals("*/*", downloadedFileMimeType("unknown"))
        assertEquals("*/*", downloadedFileMimeType("archive.custom"))
    }
}
