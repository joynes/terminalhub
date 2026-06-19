package se.joynes.terminalhub.data.ssh

data class ScpUploadProgress(
    val fileName: String,
    val bytesSent: Long,
    val totalBytes: Long
) {
    val percent: Int get() = if (totalBytes > 0) ((bytesSent * 100) / totalBytes).toInt() else 0
    val isComplete: Boolean get() = totalBytes > 0 && bytesSent >= totalBytes
}
