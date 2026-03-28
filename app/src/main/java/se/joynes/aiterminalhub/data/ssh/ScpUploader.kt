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
import java.io.InputStream
import javax.inject.Inject

/**
 * Standalone SCP uploader that opens its own raw SSH connection (no PTY, no background threads).
 * This avoids the TrileadSSH2 limitation where you can't open an exec channel concurrently
 * on a connection that already has an active PTY shell session.
 */
class ScpUploader @Inject constructor(private val logger: AppLogger) {

    private val permissiveVerifier = object : ExtendedServerHostKeyVerifier() {
        override fun verifyServerHostKey(h: String?, p: Int, a: String?, k: ByteArray?) = true
        override fun getKnownKeyAlgorithmsForHost(h: String?, p: Int) = null
        override fun removeServerHostKey(h: String?, p: Int, a: String?, k: ByteArray?) {}
        override fun addServerHostKey(h: String?, p: Int, a: String?, k: ByteArray?) {}
    }

    fun upload(
        server: Server,
        password: String?,
        privateKeyPem: String?,
        fileName: String,
        fileSize: Long,
        inputStream: InputStream,
        remoteDir: String
    ): Flow<ScpUploadProgress> = channelFlow {
        withContext(Dispatchers.IO) {
            val conn = Connection(server.host, server.port)
            try {
                conn.connect(permissiveVerifier)
                logger.log(LogLevel.INFO, TAG, "SCP auth to ${server.host}:${server.port}")

                val authenticated = when {
                    !privateKeyPem.isNullOrBlank() -> {
                        val kp = PEMDecoder.decode(privateKeyPem.toCharArray(), null)
                        conn.authenticateWithPublicKey(server.username, kp)
                    }
                    !password.isNullOrBlank() -> conn.authenticateWithPassword(server.username, password)
                    else -> conn.authenticateWithNone(server.username)
                }
                if (!authenticated) throw IOException("SCP auth failed")

                val sess = conn.openSession()
                try {
                    val sanitized = remoteDir.replace("'", "'\\''")
                    sess.execCommand("bash -lc 'scp -t \"$sanitized\"'")

                    val toRemote   = sess.stdin
                    val fromRemote = sess.stdout

                    fun readAck() {
                        val code = fromRemote.read()
                        if (code != 0) {
                            val msg = StringBuilder()
                            var c: Int
                            while (fromRemote.read().also { c = it } != '\n'.code && c != -1) msg.append(c.toChar())
                            throw IOException("SCP remote error ($code): $msg")
                        }
                    }

                    // Read initial ready-ack from scp -t server
                    readAck()

                    // Send file header and wait for ack
                    toRemote.write("C0644 $fileSize $fileName\n".toByteArray(Charsets.UTF_8))
                    toRemote.flush()
                    readAck()

                    // Stream file bytes, emit progress per chunk
                    val buf = ByteArray(8192)
                    var sent = 0L
                    var n: Int
                    while (inputStream.read(buf).also { n = it } != -1) {
                        toRemote.write(buf, 0, n)
                        sent += n
                        trySend(ScpUploadProgress(fileName, sent, fileSize))
                    }
                    toRemote.flush()

                    // End-of-file marker + final ack
                    toRemote.write(0)
                    toRemote.flush()
                    readAck()

                    trySend(ScpUploadProgress(fileName, fileSize, fileSize))
                    logger.log(LogLevel.INFO, TAG, "SCP upload complete: $fileName")
                } finally {
                    sess.close()
                }
            } finally {
                inputStream.close()
                conn.close()
            }
        }
    }

    companion object { private const val TAG = "ScpUploader" }
}
