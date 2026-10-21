package se.joynes.terminalhub.ui.screen.download

/** Monotonic elapsed time; resumed bytes are not counted as bytes transferred this attempt. */
internal class TransferRate(private val now: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private var started: Long? = null
    private var initialBytes = 0L
    fun sample(bytes: Long, total: Long): TransferEstimate {
        val time = now()
        if (started == null) { started = time; initialBytes = bytes }
        val elapsed = time - started!!
        val rate = if (elapsed >= 500) (bytes - initialBytes).coerceAtLeast(0) * 1000.0 / elapsed else 0.0
        return TransferEstimate(rate, if (rate > 0 && total > bytes) ((total - bytes) / rate).toLong() else null)
    }
}

data class TransferEstimate(val bytesPerSecond: Double = 0.0, val remainingSeconds: Long? = null) {
    fun label(): String = if (bytesPerSecond <= 0) "Measuring speed…" else
        java.lang.String.format(java.util.Locale.ROOT, "%.1f MB/s", bytesPerSecond / (1024 * 1024)) +
            (remainingSeconds?.let { " · about ${it}s left" } ?: "")
}
