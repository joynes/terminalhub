package se.joynes.aiterminalhub.domain.usecase

import kotlinx.coroutines.flow.Flow
import se.joynes.aiterminalhub.data.ssh.SftpUploader
import se.joynes.aiterminalhub.data.ssh.UploadProgress
import java.io.InputStream
import javax.inject.Inject

class UploadFiles @Inject constructor(private val sftp: SftpUploader) {
    // Session-based invocation would require JSch session access via SshManager
    // Placeholder that can be wired in once SshConnection exposes jschSession
    fun invoke(files: List<Pair<String, InputStream>>, remotePath: String): Flow<UploadProgress> {
        throw UnsupportedOperationException("Direct session access required")
    }
}
