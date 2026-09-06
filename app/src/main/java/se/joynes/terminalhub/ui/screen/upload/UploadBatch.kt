package se.joynes.terminalhub.ui.screen.upload

internal data class UploadBatchProgress(
    val completedFileNames: List<String>,
    val batchComplete: Boolean
)

/**
 * Records one completed upload without declaring the batch complete while files remain queued.
 */
internal fun advanceUploadBatch(
    completedFileNames: List<String>,
    completedFileName: String,
    remainingFiles: Int
): UploadBatchProgress = UploadBatchProgress(
    completedFileNames = completedFileNames + completedFileName,
    batchComplete = remainingFiles == 0
)
