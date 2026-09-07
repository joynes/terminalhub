package se.joynes.terminalhub.data.ssh

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

/** Lists and downloads files over temporary SFTP channels on the shared SSH transport pool. */
class ScpDownloader @Inject constructor(
    private val logger: AppLogger,
    private val transportPool: SharedSshTransportPool
) {
    suspend fun listFiles(
        server: Server,
        password: String?,
        privateKeyPem: String?,
        remoteDir: String
    ): List<RemoteFileEntry> = withContext(Dispatchers.IO) {
        val lease = transportPool.acquireTransfer(server, password, privateKeyPem)
        try {
            lease.openSftpSession().use { session ->
                val client = session.client
                val directory = resolveSftpPath(client, remoteDir)
                client.ls(directory)
                    .asSequence()
                    .filter { it.filename != "." && it.filename != ".." }
                    .filterNot { it.attributes.isSymlink }
                    .mapNotNull { entry ->
                        when {
                            entry.attributes.isDirectory -> RemoteFileEntry(entry.filename, 0L, true)
                            entry.attributes.isRegularFile -> RemoteFileEntry(
                                entry.filename,
                                entry.attributes.size ?: 0L,
                                false
                            )
                            else -> null
                        }
                    }
                    .sortedWith(compareBy<RemoteFileEntry> { !it.isDirectory }.thenBy { it.name.lowercase() })
                    .toList()
            }
        } finally {
            lease.release()
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
            requireValidTransferFileName(fileName)
            var lease: SshTransportLease? = null
            try {
                lease = transportPool.acquireTransfer(server, password, privateKeyPem)
                lease.openSftpSession().use { session ->
                    val client = session.client
                    val remotePath = joinRemotePath(resolveSftpPath(client, remoteDir), fileName)
                    val totalBytes = client.stat(remotePath).size ?: 0L
                    val handle = client.openFileRO(remotePath)
                    try {
                        val buffer = ByteArray(BUFFER_SIZE)
                        var received = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = client.read(handle, received, buffer, 0, buffer.size)
                            if (read < 0) break
                            if (read == 0) throw IOException("SFTP download stopped before completion")
                            outputStream.write(buffer, 0, read)
                            received += read
                            currentCoroutineContext().ensureActive()
                            trySend(ScpDownloadProgress(fileName, received, totalBytes))
                        }
                        if (totalBytes > 0 && received != totalBytes) {
                            throw IOException("SFTP download ended before the complete file was received")
                        }
                        outputStream.flush()
                        trySend(ScpDownloadProgress(fileName, received, totalBytes))
                        logger.log(LogLevel.INFO, TAG, "SFTP download complete: $fileName")
                    } finally {
                        client.closeFile(handle)
                    }
                }
            } finally {
                outputStream.close()
                lease?.release()
            }
        }
    }

    private companion object {
        const val TAG = "ScpDownloader"
        const val BUFFER_SIZE = 32 * 1024
    }
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
