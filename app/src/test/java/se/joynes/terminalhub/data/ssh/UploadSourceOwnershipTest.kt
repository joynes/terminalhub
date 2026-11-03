package se.joynes.terminalhub.data.ssh

import java.io.ByteArrayInputStream
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*
import se.joynes.terminalhub.data.logging.AppLogger
import se.joynes.terminalhub.data.model.Server

class UploadSourceOwnershipTest {
    @Test fun rejectedFileNameClosesSourceWithoutOpeningTransport() = runBlocking {
        val pool: SharedSshTransportPool = mock()
        val uploader = ScpUploader(mock<AppLogger>(), pool)
        var closed = false
        val input = object : ByteArrayInputStream(byteArrayOf(1, 2)) {
            override fun close() { closed = true; super.close() }
        }
        try {
            uploader.upload(Server(name = "Fixture", host = "fixture", username = "user"), null, null,
                "../escape", 2, input, "/project").toList()
            fail("Unsafe file name must fail")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("Invalid remote file name"))
        }
        assertTrue("Uploader owns and must close the source even when validation rejects it", closed)
        verifyNoInteractions(pool)
    }
}
