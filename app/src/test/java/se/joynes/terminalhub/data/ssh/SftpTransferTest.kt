package se.joynes.terminalhub.data.ssh

import com.trilead.ssh2.SFTPv3Client
import com.trilead.ssh2.SFTPv3DirectoryEntry
import com.trilead.ssh2.SFTPv3FileAttributes
import com.trilead.ssh2.SFTPv3FileHandle
import com.trilead.ssh2.Session
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import se.joynes.terminalhub.data.logging.AppLogger
import se.joynes.terminalhub.data.model.Server

class SftpTransferTest {
    private val logger: AppLogger = mock()
    private val server = Server(id = 1, name = "demo", host = "example.com", username = "alice")

    @Test
    fun `upload uses hidden part file then atomically moves it into place`() = runBlocking {
        val client: SFTPv3Client = mock()
        val handle: SFTPv3FileHandle = mock()
        whenever(client.canonicalPath(".")).thenReturn("/home/alice")
        whenever(client.createFileTruncate(any())).thenReturn(handle)
        val fixture = fixture(client)
        val payload = "file contents".toByteArray()

        val progress = ScpUploader(logger, fixture.pool).upload(
            server,
            "secret",
            null,
            "notes.txt",
            payload.size.toLong(),
            ByteArrayInputStream(payload),
            "~/terminalhub/demo"
        ).toList()

        val temporaryPath = argumentCaptor<String>()
        verify(client).createFileTruncate(temporaryPath.capture())
        assertTrue(temporaryPath.firstValue.startsWith("/home/alice/terminalhub/demo/.notes.txt."))
        assertTrue(temporaryPath.firstValue.endsWith(".part"))
        val sentBuffer = argumentCaptor<ByteArray>()
        verify(client).write(eq(handle), eq(0L), sentBuffer.capture(), eq(0), eq(payload.size))
        assertArrayEquals(payload, sentBuffer.firstValue.copyOf(payload.size))
        verify(client).closeFile(handle)
        verify(client).mv(temporaryPath.firstValue, "/home/alice/terminalhub/demo/notes.txt")
        verify(client, never()).rm(temporaryPath.firstValue)
        assertEquals(payload.size.toLong(), progress.last().bytesSent)
        assertTrue(fixture.transport.closed)
    }

    @Test
    fun `listing returns directories first and hides symlinks`() = runBlocking {
        val client: SFTPv3Client = mock()
        whenever(client.canonicalPath(".")).thenReturn("/home/alice")
        whenever(client.ls("/home/alice/terminalhub/demo/assets")).thenReturn(
            listOf(
                entry("song.wav", size = 120),
                entry("stems", directory = true),
                entry("linked", symlink = true),
                entry(".", directory = true),
                entry("..", directory = true)
            )
        )
        val fixture = fixture(client)

        val entries = ScpDownloader(logger, fixture.pool).listFiles(
            server,
            "secret",
            null,
            "~/terminalhub/demo/assets"
        )

        assertEquals(
            listOf(RemoteFileEntry("stems", 0, true), RemoteFileEntry("song.wav", 120, false)),
            entries
        )
        assertTrue(fixture.transport.closed)
    }

    @Test
    fun `failed upload removes part file without replacing destination`() = runBlocking {
        val client: SFTPv3Client = mock()
        val handle: SFTPv3FileHandle = mock()
        whenever(client.canonicalPath(".")).thenReturn("/home/alice")
        whenever(client.createFileTruncate(any())).thenReturn(handle)
        doThrow(java.io.IOException("network lost"))
            .whenever(client).write(eq(handle), eq(0L), any(), eq(0), any())
        val fixture = fixture(client)

        val failure = runCatching {
            ScpUploader(logger, fixture.pool).upload(
                server,
                "secret",
                null,
                "notes.txt",
                4,
                ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)),
                "~/terminalhub/demo"
            ).toList()
        }.exceptionOrNull()

        assertEquals("network lost", failure?.message)
        val temporaryPath = argumentCaptor<String>()
        verify(client).createFileTruncate(temporaryPath.capture())
        verify(client).closeFile(handle)
        verify(client).rm(temporaryPath.firstValue)
        verify(client, never()).mv(any(), any())
        assertTrue(fixture.transport.closed)
    }

    @Test
    fun `download streams complete remote file over pooled SFTP channel`() = runBlocking {
        val client: SFTPv3Client = mock()
        val handle: SFTPv3FileHandle = mock()
        val attributes = SFTPv3FileAttributes().also { it.size = 4L }
        val payload = byteArrayOf(1, 2, 3, 4)
        whenever(client.canonicalPath(".")).thenReturn("/home/alice")
        whenever(client.stat("/home/alice/terminalhub/demo/song.wav")).thenReturn(attributes)
        whenever(client.openFileRO("/home/alice/terminalhub/demo/song.wav")).thenReturn(handle)
        var firstRead = true
        whenever(client.read(eq(handle), any(), any(), eq(0), any())).thenAnswer { invocation ->
            if (!firstRead) return@thenAnswer -1
            firstRead = false
            val buffer = invocation.getArgument<ByteArray>(2)
            payload.copyInto(buffer)
            payload.size
        }
        val fixture = fixture(client)
        val output = ByteArrayOutputStream()

        val progress = ScpDownloader(logger, fixture.pool).download(
            server,
            "secret",
            null,
            "~/terminalhub/demo",
            "song.wav",
            output
        ).toList()

        assertEquals(payload.toList(), output.toByteArray().toList())
        assertEquals(4, progress.last().bytesTransferred)
        verify(client).closeFile(handle)
        assertTrue(fixture.transport.closed)
    }

    private fun fixture(client: SFTPv3Client): Fixture {
        val transport = FakeTransport(client)
        val connector = object : SshTransportConnector {
            override fun connect(
                server: Server,
                password: String?,
                privateKeyPem: String?
            ): SshTransport = transport
        }
        return Fixture(SharedSshTransportPool(logger, connector), transport)
    }

    private fun entry(
        name: String,
        size: Long = 0,
        directory: Boolean = false,
        symlink: Boolean = false
    ): SFTPv3DirectoryEntry = SFTPv3DirectoryEntry().also { entry ->
        entry.filename = name
        entry.attributes = SFTPv3FileAttributes().also { attributes ->
            attributes.permissions = when {
                symlink -> 0b1010 shl 12
                directory -> 0b0100 shl 12
                else -> 0b1000 shl 12
            }
            attributes.size = size
        }
    }

    private data class Fixture(val pool: SharedSshTransportPool, val transport: FakeTransport)

    private class FakeTransport(private val client: SFTPv3Client) : SshTransport {
        var closed = false
        override fun openProjectSession(): Session = mock()
        override fun openAuxiliarySession(): SshAuxiliarySession = SshAuxiliarySession(mock()) {}
        override fun openSftpSession(): SshAuxiliarySftp = SshAuxiliarySftp(client) {}
        override fun updateProjectIds(projectIds: Set<Long>) = Unit
        override fun close() { closed = true }
        override fun debugIdentity(): Int = 1
    }
}
