package se.joynes.terminalhub.ui.screen.upload

internal data class UploadBatchProgress(
    val completedRemotePaths: List<String>,
    val batchComplete: Boolean
)

/**
 * Records one completed upload without declaring the batch complete while files remain queued.
 */
internal fun advanceUploadBatch(
    completedRemotePaths: List<String>,
    completedRemotePath: String,
    remainingFiles: Int
): UploadBatchProgress = UploadBatchProgress(
    completedRemotePaths = completedRemotePaths + completedRemotePath,
    batchComplete = remainingFiles == 0
)
