package se.joynes.terminalhub.data.ssh

import java.io.File
import java.io.OutputStream

/** Retry checkpoint for one queued file. Partial bytes never appear in the user's destination.
 * Resume is intentionally conservative: missing or changed server metadata restarts the file.
 * Checkpoints last for this ViewModel's lifetime, not across Android process death.
 */
class StagedRemoteDownload(val file: File) {
    private val active = java.util.concurrent.atomic.AtomicReference<AutoCloseable?>()
    private val cancelled = java.util.concurrent.atomic.AtomicBoolean(false)
    private data class Stamp(val path: String, val size: Long, val modified: Long)
    private var stamp: Stamp? = null

    internal fun attach(channel: AutoCloseable) {
        active.set(channel)
        if (cancelled.get()) { channel.close(); throw kotlinx.coroutines.CancellationException() }
    }
    internal fun detach() { active.set(null) }
    fun cancel() {
        cancelled.set(true)
        // Closing only this SFTP channel wakes a blocked read, not the shared SSH connection.
        active.getAndSet(null)?.close()
    }

    internal fun open(path: String, size: Long?, modified: Long?): Pair<OutputStream, Long> {
        val next = if (size != null && modified != null) Stamp(path, size, modified) else null
        val resume = next != null && next == stamp && file.length() in 0..next.size
        val offset = if (resume) file.length() else 0L
        stamp = next
        return file.outputStreamForResume(resume) to offset
    }

    private fun File.outputStreamForResume(append: Boolean) = java.io.FileOutputStream(this, append)
}
