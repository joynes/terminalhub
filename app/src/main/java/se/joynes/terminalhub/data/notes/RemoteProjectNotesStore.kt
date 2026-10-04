package se.joynes.terminalhub.data.notes

import com.trilead.ssh2.*
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import se.joynes.terminalhub.data.model.Project
import se.joynes.terminalhub.data.model.Server
import se.joynes.terminalhub.data.security.SecurePrefsManager
import se.joynes.terminalhub.data.ssh.SharedSshTransportPool
import se.joynes.terminalhub.domain.ScriptTemplateEngine

const val MAX_NOTE_BYTES = 256 * 1024
fun noteHash(text: String): String = noteBytesHash(text.toByteArray(Charsets.UTF_8))
private fun noteBytesHash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes).joinToString("") { "%02x".format(it) }
fun stableNoteKey(canonicalPath: String): String = noteHash(canonicalPath.trimEnd('/').ifEmpty { "/" })
internal fun normalizeRemoteProjectPath(path: String): String {
    val parts = mutableListOf<String>()
    path.split('/').forEach { part ->
        when (part) {
            "", "." -> Unit
            ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
            else -> parts.add(part)
        }
    }
    return "/" + parts.joinToString("/")
}

data class RemoteNote(val key: String, val text: String?, val mtime: Long?) {
    val hash: String? get() = text?.let(::noteHash)
}
class NoteConflict(val remote: RemoteNote) : IOException("Remote note changed")

@Singleton
class RemoteProjectNotesStore @Inject constructor(
    private val pool: SharedSshTransportPool,
    private val credentials: SecurePrefsManager,
    private val templates: ScriptTemplateEngine
) {
    suspend fun read(server: Server, project: Project): RemoteNote = withClient(server) { client ->
        val (key, path) = identity(client, templates.projectPath(server, project))
        readAt(client, key, path)
    }

    suspend fun write(server: Server, project: Project, text: String, expected: RemoteNote, delete: Boolean = false): RemoteNote =
        withClient(server) { client ->
            val (key, path) = identity(client, templates.projectPath(server, project))
            val current = readAt(client, key, path)
            if (key != expected.key || current.hash != expected.hash) throw NoteConflict(current)
            if (delete) {
                if (current.text != null) client.rm(path)
                RemoteNote(key, null, null)
            } else {
                val bytes = text.toByteArray(Charsets.UTF_8)
                require(bytes.size <= MAX_NOTE_BYTES) { "Note exceeds 256 KB" }
                val home = client.canonicalPath(".").trimEnd('/')
                var directory = home
                for (part in listOf(".terminalhub", "project-notes", key)) {
                    directory += "/$part"
                    try { client.mkdir(directory, 448) } catch (error: IOException) {
                        val attrs = client.lstat(directory)
                        if (!attrs.isDirectory || attrs.isSymlink) throw error
                    }
                }
                val temporary = "$directory/.note.md.${UUID.randomUUID()}.part"
                try {
                    val handle = client.createFile(temporary, SFTPv3FileAttributes().apply { permissions = 384 })
                    try {
                        var offset = 0
                        while (offset < bytes.size) {
                            val count = minOf(32768, bytes.size - offset)
                            client.write(handle, offset.toLong(), bytes, offset, count)
                            offset += count
                        }
                    } finally { client.closeFile(handle) }
                    // Check again after upload, before publishing the complete file.
                    val latest = readAt(client, key, path)
                    if (latest.hash != expected.hash) throw NoteConflict(latest)
                    if (latest.text == null) client.mv(temporary, path)
                    else AtomicSftpRename.replace(client, temporary, path)
                    readAt(client, key, path)
                } finally { runCatching { client.rm(temporary) } }
            }
        }

    private suspend fun <T> withClient(server: Server, block: (SFTPv3Client) -> T): T = withContext(Dispatchers.IO) {
        val lease = pool.acquireTransfer(server, credentials.getPassword(server.id), credentials.getPrivateKey(server.id))
        try {
            lease.openSftpSession().use { session ->
                session.client.setCharset("UTF-8")
                // Close just this auxiliary channel to unblock a stalled read, not all tabs.
                coroutineScope {
                    val watchdog = launch(Dispatchers.IO) { delay(30_000); session.close() }
                    try { block(session.client) } finally { watchdog.cancel() }
                }
            }
        } finally { lease.release() }
    }

    internal fun identity(client: SFTPv3Client, configured: String): Pair<String, String> {
        val home = client.canonicalPath(".").trimEnd('/')
        val resolved = when {
            configured == "~" -> home
            configured.startsWith("~/") -> "$home/${configured.removePrefix("~/")}"
            configured.startsWith('/') -> configured
            else -> "$home/$configured"
        }
        val canonical = try { client.canonicalPath(resolved) } catch (error: SFTPException) {
            if (error.serverErrorCode != 2) throw error
            normalizeRemoteProjectPath(resolved)
        }
        val key = stableNoteKey(canonical)
        return key to "$home/.terminalhub/project-notes/$key/note.md"
    }

    internal fun readAt(client: SFTPv3Client, key: String, path: String): RemoteNote {
        val attrs = try { client.lstat(path) } catch (error: SFTPException) {
            if (error.serverErrorCode == 2) return RemoteNote(key, null, null)
            throw error
        }
        require(!attrs.isSymlink && attrs.isRegularFile) { "Not a regular note file" }
        require((attrs.size ?: 0) <= MAX_NOTE_BYTES) { "Note exceeds 256 KB" }
        val output = ByteArrayOutputStream()
        val handle = client.openFileRO(path)
        try {
            val buffer = ByteArray(32768)
            while (true) {
                val count = client.read(handle, output.size().toLong(), buffer, 0, buffer.size)
                if (count < 0) break
                if (count == 0) throw IOException("Empty SFTP read")
                require(output.size() + count <= MAX_NOTE_BYTES) { "Note exceeds 256 KB" }
                output.write(buffer, 0, count)
            }
        } finally { client.closeFile(handle) }
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(output.toByteArray())).toString()
        return RemoteNote(key, text, attrs.mtime?.toLong())
    }
}
