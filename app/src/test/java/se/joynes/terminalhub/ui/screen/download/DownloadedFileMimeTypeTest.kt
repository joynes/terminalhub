package se.joynes.terminalhub.ui.screen.download

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadedFileMimeTypeTest {
    @Test
    fun audioFilesUseTypesThatMobileAudioAppsCanHandle() {
        assertEquals("audio/wav", downloadedFileMimeType("recording.wav"))
        assertEquals("audio/mp4", downloadedFileMimeType("song.m4a"))
        assertEquals("audio/midi", downloadedFileMimeType("arrangement.mid"))
    }

    @Test
    fun extensionMatchingIsCaseInsensitive() {
        assertEquals("application/pdf", downloadedFileMimeType("MANUAL.PDF"))
    }

    @Test
    fun unknownFilesAllowAndroidToOfferAnyCompatibleApp() {
        assertEquals("*/*", downloadedFileMimeType("archive.custom"))
        assertEquals("*/*", downloadedFileMimeType("Makefile"))
    }
}
