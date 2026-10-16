package se.joynes.terminalhub.ui.screen.download

import android.net.Uri

internal data class PendingFolderDownload(
    val serverId: Long, val projectId: Long, val directory: String, val fileNames: List<String>
)

/** Separates choosing a folder from downloading; a folder change never sends files. */
internal class RemoteDownloadFolderController(
    private val remembered: () -> Uri?,
    private val isWritable: suspend (Uri) -> Boolean,
    private val forget: () -> Unit,
    private val start: (PendingFolderDownload, Uri) -> Unit,
    private val choose: (PendingFolderDownload?, Uri?) -> Unit
) {
    suspend fun download(request: PendingFolderDownload) {
        val saved = remembered()
        if (saved != null && isWritable(saved)) start(request, saved) else {
            forget()
            choose(request, saved)
        }
    }

    fun changeFolder() = choose(null, remembered())
}
