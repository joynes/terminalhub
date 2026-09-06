package se.joynes.terminalhub.data.ssh

import com.trilead.ssh2.Connection
import com.trilead.ssh2.crypto.PEMDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import se.joynes.terminalhub.data.logging.AppLogger
import se.joynes.terminalhub.data.logging.LogLevel
import se.joynes.terminalhub.data.model.Server
import java.io.IOException
import java.io.OutputStream
import javax.inject.Inject

data class RemoteFileEntry(
    val name: String,
    val size: Long,
    val isDirectory: Boolean = false
)

data class ScpDownloadProgress(
    val fileName: String,
    val bytesTransferred: Long,
    val totalBytes: Long
) {
    val percent: Int get() = if (totalBytes > 0) ((bytesTransferred * 100) / totalBytes).toInt() else 0
}

class ScpDownloader @Inject constructor(
    private val logger: AppLogger,
    private val hostKeyVerifier: TerminalHubHostKeyVerifier
) {

    suspend fun listFiles(
        server: Server,
        password: String?,
        privateKeyPem: String?,
        remoteDir: String
    ): List<RemoteFileEntry> = withContext(Dispatchers.IO) {
        val conn = connect(server, password, privateKeyPem)
        try {
            val sess = conn.openSession()
            try {
                sess.execCommand(remoteFileListCommand(remoteDir))
                val stderr = sess.stderr.reader().readText().trim()
                val rows = sess.stdout.reader().readLines()
                if (stderr.contains(REMOTE_DIR_MISSING_MARKER)) {
                    throw IOException("Remote project folder not found: $remoteDir")
                }
                val entries = rows.mapNotNull { row ->
                    val parts = row.split('\t')
                    val type = parts.getOrNull(0) ?: return@mapNotNull null
                    if (type != "d" && type != "f") return@mapNotNull null
                    val name = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val size = parts.getOrNull(2)?.toLongOrNull() ?: 0L
                    RemoteFileEntry(name, size, isDirectory = type == "d")
                }.sortedWith(compareBy<RemoteFileEntry> { !it.isDirectory }.thenBy { it.name.lowercase() })
                if (stderr.isNotBlank()) {
                    logger.log(LogLevel.WARN, TAG, "Remote list stderr: $stderr")
                }
                entries
            } finally {
                sess.close()
            }
        } finally {
            conn.close()
        }
    }

    fun download(
        server: Server,
        password: String?,
        privateKeyPem: String?,
        remoteDir: String,
        fileName: String,
        outputStream: OutputStream
    ): Flow<ScpDownloadProgress> = channelFlow {
        withContext(Dispatchers.IO) {
            require('/' !in fileName) { "Invalid remote file name" }
            val conn = connect(server, password, privateKeyPem)
            try {
                val sess = conn.openSession()
                try {
                    val remotePath = "${remoteDir.trimEnd('/')}/$fileName"
                    sess.execCommand("scp -f ${shellRemotePath(remotePath)}")

                    val fromRemote = sess.stdout
                    val toRemote = sess.stdin
                    val buffer = ByteArray(8192)

                    fun ack() {
                        toRemote.write(0)
                        toRemote.flush()
                    }

                    ack()
                    val headerCode = fromRemote.read()
                    if (headerCode != 'C'.code) {
                        throw IOException("SCP remote error: ${readLine(fromRemote)}")
                    }
                    val mode = readToken(fromRemote)
                    val size = readToken(fromRemote).toLongOrNull() ?: throw IOException("Invalid SCP size")
                    val remoteName = readLine(fromRemote).ifBlank { fileName }
                    logger.log(LogLevel.INFO, TAG, "Downloading $remoteName mode=$mode size=$size")
                    ack()

                    var remaining = size
                    var received = 0L
                    while (remaining > 0) {
                        val read = fromRemote.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                        if (read < 0) throw IOException("Unexpected EOF during download")
                        outputStream.write(buffer, 0, read)
                        remaining -= read
                        received += read
                        trySend(ScpDownloadProgress(fileName, received, size))
                    }
                    outputStream.flush()

                    val endAck = fromRemote.read()
                    if (endAck != 0) throw IOException("SCP transfer did not finish cleanly")
                    ack()
                    trySend(ScpDownloadProgress(fileName, size, size))
                } finally {
                    sess.close()
                }
            } finally {
                outputStream.close()
                conn.close()
            }
        }
    }

    private fun connect(server: Server, password: String?, privateKeyPem: String?): Connection {
        val conn = Connection(server.host, server.port)
        withVerifiedHostKey(hostKeyVerifier, server.host, server.port) {
            conn.connect(hostKeyVerifier)
        }
        val authenticated = when {
            !privateKeyPem.isNullOrBlank() -> {
                val kp = PEMDecoder.decode(privateKeyPem.toCharArray(), null)
                conn.authenticateWithPublicKey(server.username, kp)
            }
            !password.isNullOrBlank() -> conn.authenticateWithPassword(server.username, password)
            else -> conn.authenticateWithNone(server.username)
        }
        if (!authenticated) {
            conn.close()
            throw IOException("SCP auth failed")
        }
        return conn
    }

    private fun readToken(input: java.io.InputStream): String {
        val out = StringBuilder()
        while (true) {
            val c = input.read()
            if (c < 0 || c == ' '.code) break
            out.append(c.toChar())
        }
        return out.toString()
    }

    private fun readLine(input: java.io.InputStream): String {
        val out = StringBuilder()
        while (true) {
            val c = input.read()
            if (c < 0 || c == '\n'.code) break
            out.append(c.toChar())
        }
        return out.toString()
    }

    companion object {
        private const val TAG = "ScpDownloader"
        internal const val REMOTE_DIR_MISSING_MARKER = "AITERM_REMOTE_DIR_MISSING"
    }
}

/**
 * Force the glob loop through POSIX sh. A remote account may use zsh with NOMATCH enabled, which
 * aborts the whole command when one of the optional hidden-file globs has no matches.
 */
internal fun remoteFileListCommand(remoteDir: String): String {
    val script = "if [ ! -d \"\$1\" ]; then " +
        "printf '${ScpDownloader.REMOTE_DIR_MISSING_MARKER}\\n' >&2; exit 3; fi; " +
        "for f in \"\$1\"/* \"\$1\"/.[!.]* \"\$1\"/..?*; do " +
        "[ -L \"\$f\" ] && continue; " +
        "if [ -d \"\$f\" ]; then type=d; size=0; " +
        "elif [ -f \"\$f\" ]; then type=f; size=\$(wc -c < \"\$f\" | tr -d ' '); " +
        "else continue; fi; " +
        "name=\${f##*/}; " +
        "printf '%s\\t%s\\t%s\\n' \"\$type\" \"\$name\" \"\$size\"; " +
        "done"
    return "sh -c ${shellSingleQuote(script)} sh ${shellRemotePath(remoteDir)}"
}

internal fun remoteSubdirectory(projectRoot: String, relativeDirectory: String): String {
    val segments = relativeDirectory.split('/').filter { it.isNotEmpty() }
    require(!relativeDirectory.startsWith('/')) { "Invalid remote directory" }
    require(segments.all { it != "." && it != ".." && '\u0000' !in it && '\\' !in it }) {
        "Invalid remote directory"
    }
    return if (segments.isEmpty()) projectRoot.trimEnd('/')
    else projectRoot.trimEnd('/') + "/" + segments.joinToString("/")
}

internal fun shellRemotePath(path: String): String {
    fun escapeDoubleQuoted(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("`", "\\`")
        .replace("\$", "\\\$")

    return if (path.startsWith("~/")) {
        "\"\$HOME/${escapeDoubleQuoted(path.removePrefix("~/"))}\""
    } else {
        "\"${escapeDoubleQuoted(path)}\""
    }
}

private fun shellSingleQuote(value: String): String =
    "'" + value.replace("'", "'\\''") + "'"
