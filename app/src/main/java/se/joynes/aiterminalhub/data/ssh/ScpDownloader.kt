package se.joynes.aiterminalhub.data.ssh

import com.trilead.ssh2.Connection
import com.trilead.ssh2.ExtendedServerHostKeyVerifier
import com.trilead.ssh2.crypto.PEMDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import se.joynes.aiterminalhub.data.logging.AppLogger
import se.joynes.aiterminalhub.data.logging.LogLevel
import se.joynes.aiterminalhub.data.model.Server
import java.io.IOException
import java.io.OutputStream
import javax.inject.Inject

data class RemoteFileEntry(
    val name: String,
    val size: Long
)

data class ScpDownloadProgress(
    val fileName: String,
    val bytesTransferred: Long,
    val totalBytes: Long
) {
    val percent: Int get() = if (totalBytes > 0) ((bytesTransferred * 100) / totalBytes).toInt() else 0
}

class ScpDownloader @Inject constructor(private val logger: AppLogger) {

    private val permissiveVerifier = object : ExtendedServerHostKeyVerifier() {
        override fun verifyServerHostKey(h: String?, p: Int, a: String?, k: ByteArray?) = true
        override fun getKnownKeyAlgorithmsForHost(h: String?, p: Int) = null
        override fun removeServerHostKey(h: String?, p: Int, a: String?, k: ByteArray?) {}
        override fun addServerHostKey(h: String?, p: Int, a: String?, k: ByteArray?) {}
    }

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
                val dir = shellDoubleQuote(expandHome(remoteDir))
                sess.execCommand(
                    "for f in $dir/* $dir/.[!.]* $dir/..?*; do " +
                        "[ -f \"\$f\" ] || continue; " +
                        "size=\$(wc -c < \"\$f\" | tr -d \" \"); " +
                        "name=\${f##*/}; " +
                        "printf \"%s\\t%s\\n\" \"\$name\" \"\$size\"; " +
                        "done"
                )
                val stderr = sess.stderr.reader().readText().trim()
                val rows = sess.stdout.reader().readLines()
                val files = rows.mapNotNull { row ->
                    val parts = row.split('\t')
                    val name = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val size = parts.getOrNull(1)?.toLongOrNull() ?: 0L
                    RemoteFileEntry(name, size)
                }.sortedBy { it.name.lowercase() }
                if (files.isEmpty() && stderr.isNotBlank()) {
                    logger.log(LogLevel.DEBUG, TAG, "Remote list stderr: $stderr")
                }
                files
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
                    val remotePath = "${expandHome(remoteDir).trimEnd('/')}/$fileName"
                    sess.execCommand("scp -f ${shellDoubleQuote(remotePath)}")

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
        conn.connect(permissiveVerifier)
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

    private fun expandHome(path: String): String =
        if (path.startsWith("~/")) "\$HOME${path.substring(1)}" else path

    private fun shellDoubleQuote(value: String): String =
        "\"" + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("`", "\\`") + "\""

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

    companion object { private const val TAG = "ScpDownloader" }
}
