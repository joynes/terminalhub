package se.joynes.aiterminalhub.data.ssh

import android.net.Uri
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.Session
import com.jcraft.jsch.SftpProgressMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import se.joynes.aiterminalhub.data.logging.AppLogger
import se.joynes.aiterminalhub.data.logging.LogLevel
import java.io.InputStream
import javax.inject.Inject

data class UploadProgress(val fileName: String, val bytesTransferred: Long, val totalBytes: Long) {
    val percent: Int get() = if (totalBytes > 0) ((bytesTransferred * 100) / totalBytes).toInt() else 0
    val isComplete: Boolean get() = bytesTransferred >= totalBytes && totalBytes > 0
}

class SftpUploader @Inject constructor(
    private val logger: AppLogger
) {
    private val TAG = "SftpUploader"

    fun upload(session: Session, files: List<Pair<String, InputStream>>, remotePath: String): Flow<UploadProgress> = flow {
        val channel = session.openChannel("sftp") as ChannelSftp
        channel.connect()
        logger.log(LogLevel.INFO, TAG, "SFTP channel opened, uploading ${files.size} file(s) to $remotePath")
        try {
            for ((name, stream) in files) {
                logger.log(LogLevel.INFO, TAG, "Uploading $name...")
                val totalBytes = stream.available().toLong()
                var transferred = 0L
                val progressMonitor = object : SftpProgressMonitor {
                    override fun init(op: Int, src: String, dest: String, max: Long) {
                        logger.log(LogLevel.DEBUG, TAG, "Upload init: $src -> $dest (${max} bytes)")
                    }
                    override fun count(count: Long): Boolean {
                        transferred += count
                        return true
                    }
                    override fun end() {
                        logger.log(LogLevel.INFO, TAG, "Upload complete: $name")
                    }
                }
                channel.put(stream, "$remotePath/$name", progressMonitor)
                stream.close()
                emit(UploadProgress(name, totalBytes, totalBytes))
            }
        } finally {
            channel.disconnect()
            logger.log(LogLevel.INFO, TAG, "SFTP channel closed")
        }
    }.flowOn(Dispatchers.IO)
}
