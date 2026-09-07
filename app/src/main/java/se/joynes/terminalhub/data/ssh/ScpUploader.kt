package se.joynes.terminalhub.data.ssh

import com.trilead.ssh2.SFTPv3Client
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import se.joynes.terminalhub.data.logging.AppLogger
import se.joynes.terminalhub.data.logging.LogLevel
import se.joynes.terminalhub.data.model.Server
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject

/**
 * Uploads through a short-lived SFTP channel on the shared authenticated SSH transport.
 *
 * The legacy class name is retained to avoid changing the UI contract. A hidden temporary file is
 * used so interrupted uploads never appear as complete files in the project directory.
 */
class ScpUploader @Inject constructor(
    private val logger: AppLogger,
    private val transportPool: SharedSshTransportPool
) {
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
            requireValidTransferFileName(fileName)
            var lease: SshTransportLease? = null
            try {
                lease = transportPool.acquireTransfer(server, password, privateKeyPem)
                lease.openSftpSession().use { session ->
                    val client = session.client
                    val resolvedDir = resolveSftpPath(client, remoteDir)
                    val destination = joinRemotePath(resolvedDir, fileName)
                    val temporary = joinRemotePath(resolvedDir, ".$fileName.${UUID.randomUUID()}.part")
                    var completed = false
                    try {
                        var sent = 0L
                        val handle = client.createFileTruncate(temporary)
                        try {
                            val buffer = ByteArray(BUFFER_SIZE)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = inputStream.read(buffer)
                                if (read < 0) break
                                client.write(handle, sent, buffer, 0, read)
                                sent += read
                                currentCoroutineContext().ensureActive()
                                trySend(ScpUploadProgress(fileName, sent, fileSize))
                            }
                            if (fileSize > 0 && sent != fileSize) {
                                throw IOException("Upload source changed while reading $fileName")
                            }
                        } finally {
                            client.closeFile(handle)
                        }
                        moveReplacingExisting(client, temporary, destination)
                        completed = true
                        val totalBytes = fileSize.takeIf { it > 0 } ?: sent
                        trySend(ScpUploadProgress(fileName, sent, totalBytes))
                        logger.log(LogLevel.INFO, TAG, "SFTP upload complete: $fileName")
                    } finally {
                        if (!completed) runCatching { client.rm(temporary) }
                    }
                }
            } finally {
                inputStream.close()
                lease?.release()
            }
        }
    }

    private companion object {
        const val TAG = "ScpUploader"
        const val BUFFER_SIZE = 32 * 1024
    }
}

internal fun requireValidTransferFileName(fileName: String) {
    require(fileName.isNotBlank() && fileName != "." && fileName != "..") {
        "Invalid remote file name"
    }
    require('/' !in fileName && '\\' !in fileName && '\u0000' !in fileName) {
        "Invalid remote file name"
    }
}

internal fun resolveSftpPath(client: SFTPv3Client, path: String): String = when {
    path == "~" -> client.canonicalPath(".")
    path.startsWith("~/") -> joinRemotePath(client.canonicalPath("."), path.removePrefix("~/"))
    else -> path
}

internal fun joinRemotePath(directory: String, child: String): String =
    directory.trimEnd('/') + "/" + child

internal fun moveReplacingExisting(client: SFTPv3Client, source: String, destination: String) {
    try {
        client.mv(source, destination)
    } catch (firstError: Exception) {
        try {
            client.rm(destination)
        } catch (_: Exception) {
            throw firstError
        }
        client.mv(source, destination)
    }
}
