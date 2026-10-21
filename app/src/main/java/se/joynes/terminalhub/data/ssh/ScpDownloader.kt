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
import com.trilead.ssh2.PipelinedSftpDownload
import javax.inject.Inject

data class RemoteFileEntry(
    val name: String,
    val size: Long,
    val isDirectory: Boolean = false,
    val modifiedAtEpochSeconds: Long = 0L
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
                            entry.attributes.isDirectory -> RemoteFileEntry(
                                name = entry.filename,
                                size = 0L,
                                isDirectory = true,
                                modifiedAtEpochSeconds = entry.attributes.mtime ?: 0L
                            )
                            entry.attributes.isRegularFile -> RemoteFileEntry(
                                name = entry.filename,
                                size = entry.attributes.size ?: 0L,
                                isDirectory = false,
                                modifiedAtEpochSeconds = entry.attributes.mtime ?: 0L
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
    ): Flow<ScpDownloadProgress> = transfer(server, password, privateKeyPem, remoteDir, fileName, outputStream) { _, _, _ ->
        outputStream to 0L
    }

    fun downloadToFile(
        server: Server, password: String?, privateKeyPem: String?, remoteDir: String,
        fileName: String, staging: StagedRemoteDownload
    ): Flow<ScpDownloadProgress> = transfer(server, password, privateKeyPem, remoteDir, fileName, staging = staging, destination = staging::open)

    private fun transfer(
        server: Server, password: String?, privateKeyPem: String?, remoteDir: String,
        fileName: String, initialOutput: OutputStream? = null, staging: StagedRemoteDownload? = null,
        destination: (String, Long?, Long?) -> Pair<OutputStream, Long>
    ): Flow<ScpDownloadProgress> = channelFlow {
        withContext(Dispatchers.IO) {
            requireValidTransferFileName(fileName)
            var lease: SshTransportLease? = null
            var outputStream: OutputStream? = initialOutput
            try {
                lease = transportPool.acquireTransfer(server, password, privateKeyPem)
                lease.openSftpSession().use { session ->
                    staging?.attach(session)
                    val client = session.client
                    val remotePath = joinRemotePath(resolveSftpPath(client, remoteDir), fileName)
                    val attributes = client.stat(remotePath)
                    val totalBytes = attributes.size
                    val (stream, start) = destination(remotePath, totalBytes, attributes.mtime)
                    outputStream = stream
                    val handle = client.openFileRO(remotePath)
                    var complete = false
                    try {
                        var received = start
                        trySend(ScpDownloadProgress(fileName, received, totalBytes ?: 0))
                        val context = currentCoroutineContext()
                        if (totalBytes != null) {
                            PipelinedSftpDownload.copy(client, handle, totalBytes, start, stream) { count ->
                                context.ensureActive()
                                if (count != received) {
                                    received = count
                                    trySend(ScpDownloadProgress(fileName, received, totalBytes))
                                }
                            }
                        } else {
                            // Servers without a known size retain the compatible streaming path.
                            val buffer = ByteArray(BUFFER_SIZE)
                            while (true) {
                                context.ensureActive()
                                val read = client.read(handle, received, buffer, 0, buffer.size)
                                if (read < 0) break
                                if (read == 0) throw IOException("SFTP download stopped before completion")
                                stream.write(buffer, 0, read)
                                received += read
                                trySend(ScpDownloadProgress(fileName, received, 0))
                            }
                        }
                        val finalAttributes = client.fstat(handle)
                        if ((totalBytes != null && finalAttributes.size != totalBytes) ||
                            (attributes.mtime != null && finalAttributes.mtime != attributes.mtime)) {
                            throw IOException("Remote file changed during download. Retry to download its current version.")
                        }
                        stream.flush()
                        complete = true
                        trySend(ScpDownloadProgress(fileName, received, totalBytes ?: 0))
                        logger.log(LogLevel.INFO, TAG, "SFTP download complete: $fileName")
                    } finally {
                        // On failure there may be read replies still in flight. Closing the entire
                        // owned channel releases its handles without misreading those as CLOSE replies.
                        if (complete) client.closeFile(handle)
                    }
                }
            } finally {
                staging?.detach()
                try { outputStream?.close() } finally { lease?.release() }
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
