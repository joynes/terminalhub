package se.joynes.terminalhub.data.notes

import com.trilead.ssh2.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.*
import se.joynes.terminalhub.data.model.*
import se.joynes.terminalhub.data.logging.AppLogger
import se.joynes.terminalhub.data.security.SecurePrefsManager
import se.joynes.terminalhub.data.ssh.*
import se.joynes.terminalhub.domain.ScriptTemplateEngine
import java.io.IOException

class RemoteProjectNotesStoreTest {
    private val server = Server(id = 1, name = "test", host = "test", username = "test")
    private val project = Project(id = 2, serverId = 1, name = "demo")
    private val client: SFTPv3Client = mock()
    private val files = mutableMapOf<String, ByteArray>()
    private val handles = mutableMapOf<SFTPv3FileHandle, String>()
    private var opens = 0
    private var releases = 0
    private fun fixture(): RemoteProjectNotesStore {
        whenever(client.canonicalPath(".")).thenReturn("/home/test")
        whenever(client.canonicalPath("/home/test/terminalhub/demo")).thenReturn("/srv/demo/")
        whenever(client.lstat(any())).thenAnswer { invocation ->
            val data = files[invocation.getArgument<String>(0)] ?: throw mock<SFTPException> { on { serverErrorCode } doReturn 2 }
            SFTPv3FileAttributes().apply { permissions = 0x8000 or 384; size = data.size.toLong(); mtime = 10 }
        }
        whenever(client.createFile(any(), any())).thenAnswer { invocation ->
            val path = invocation.getArgument<String>(0)
            files[path] = byteArrayOf()
            mock<SFTPv3FileHandle>().also { handles[it] = path }
        }
        whenever(client.openFileRO(any())).thenAnswer { invocation ->
            mock<SFTPv3FileHandle>().also { handles[it] = invocation.getArgument(0) }
        }
        doAnswer { invocation ->
            val path = handles.getValue(invocation.getArgument(0))
            val data = invocation.getArgument<ByteArray>(2)
            files[path] = files.getValue(path) + data.copyOfRange(invocation.getArgument(3), invocation.getArgument<Int>(3) + invocation.getArgument<Int>(4))
            null
        }.whenever(client).write(any(), any(), any(), any(), any())
        whenever(client.read(any(), any(), any(), any(), any())).thenAnswer { invocation ->
            val bytes = files.getValue(handles.getValue(invocation.getArgument(0)))
            val offset = invocation.getArgument<Long>(1).toInt()
            if (offset >= bytes.size) -1 else {
                val count = minOf(bytes.size - offset, invocation.getArgument<Int>(4))
                bytes.copyInto(invocation.getArgument(2), invocation.getArgument(3), offset, offset + count)
                count
            }
        }
        doAnswer { invocation -> files[invocation.getArgument(1)] = files.remove(invocation.getArgument<String>(0))!!; null }
            .whenever(client).mv(any(), any())
        doAnswer { invocation -> files.remove(invocation.getArgument<String>(0)); null }.whenever(client).rm(any())
        val transport = object : SshTransport {
            override fun openProjectSession(): Session = mock()
            override fun openAuxiliarySession() = SshAuxiliarySession(mock()) {}
            override fun openSftpSession(): SshAuxiliarySftp { opens++; return SshAuxiliarySftp(client) { releases++ } }
            override fun updateProjectIds(projectIds: Set<Long>) {}
            override fun close() {}
            override fun debugIdentity() = 1
        }
        val pool = SharedSshTransportPool(mock<AppLogger>(), object : SshTransportConnector {
            override fun connect(server: Server, password: String?, privateKeyPem: String?) = transport
        })
        return RemoteProjectNotesStore(pool, mock<SecurePrefsManager>(), ScriptTemplateEngine())
    }

    @Test fun `server source survives local reinstall and remapped ids with exact utf8`() = runBlocking {
        val store = fixture()
        val missing = store.read(server, project)
        assertNull(missing.text)
        assertTrue(files.isEmpty())
        val text = "# Plan åäö 😀\r\n- quoted 'text' \\\n\$HOME\n"
        val saved = store.write(server, project, text, missing)
        assertEquals(text, saved.text)
        assertEquals(text, store.read(server.copy(id = 71), project.copy(id = 99, serverId = 71)).text)
        assertEquals(stableNoteKey("/srv/demo"), saved.key)
        assertEquals(1, files.size)
        verify(client).mkdir("/home/test/.terminalhub", 448)
        verify(client, atLeastOnce()).closeFile(any())
        assertEquals(opens, releases)
        store.write(server, project, "", saved, delete = true)
        assertTrue(files.isEmpty())
    }

    @Test fun `failed publication never removes old note and cleans temporary file`() = runBlocking {
        val store = fixture()
        val saved = store.write(server, project, "old", store.read(server, project))
        Mockito.mockStatic(AtomicSftpRename::class.java).use { atomic ->
            atomic.`when`<Unit> { AtomicSftpRename.replace(any(), any(), any()) }.thenThrow(IOException("Interrupted"))
            assertTrue(runCatching { store.write(server, project, "new", saved) }.isFailure)
        }
        assertEquals("old", store.read(server, project).text)
        assertEquals(1, files.size)
        assertEquals(opens, releases)
    }

    @Test fun `remote edits become conflicts and oversized notes never truncate`() = runBlocking {
        val store = fixture()
        val saved = store.write(server, project, "old", store.read(server, project))
        val path = files.keys.single()
        files[path] = "server edit".toByteArray()
        assertTrue(runCatching { store.write(server, project, "device edit", saved) }.exceptionOrNull() is NoteConflict)
        assertEquals("server edit", store.read(server, project).text)
        files[path] = ByteArray(MAX_NOTE_BYTES + 1)
        assertTrue(runCatching { store.read(server, project) }.isFailure)
        assertEquals(MAX_NOTE_BYTES + 1, files.getValue(path).size)
    }
}
