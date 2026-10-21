package se.joynes.terminalhub.data.ssh

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StagedRemoteDownloadTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun resumesOnlyUnchangedRemoteFile() {
        val stage = StagedRemoteDownload(temporary.newFile())
        stage.open("/file", 10, 7).first.use { it.write(byteArrayOf(1, 2)) }
        val (stream, offset) = stage.open("/file", 10, 7)
        assertEquals(2L, offset)
        stream.use { it.write(3) }
        assertArrayEquals(byteArrayOf(1, 2, 3), stage.file.readBytes())
    }
    @Test fun changedIdentityOrMissingMetadataAlwaysRestarts() {
        for ((path, size, modified) in listOf(
            Triple("/other", 10L, 7L), Triple("/file", 11L, 7L), Triple("/file", 10L, 8L),
            Triple("/file", null, 7L), Triple("/file", 10L, null)
        )) {
            val stage = StagedRemoteDownload(temporary.newFile())
            stage.open("/file", 10, 7).first.use { it.write(1) }
            val (stream, offset) = stage.open(path, size, modified)
            stream.close()
            assertEquals(0L, offset)
            assertEquals(0L, stage.file.length())
        }
    }
    @Test fun cancellationClosesOnlyTheOwnedChannelOnceAndLateAttachIsCancelled() {
        val stage = StagedRemoteDownload(temporary.newFile())
        var closes = 0
        stage.attach(AutoCloseable { closes++ })
        stage.cancel(); stage.cancel()
        assertEquals(1, closes)
        assertThrows(kotlinx.coroutines.CancellationException::class.java) {
            stage.attach(AutoCloseable { closes++ })
        }
        assertEquals(2, closes)
    }
}
