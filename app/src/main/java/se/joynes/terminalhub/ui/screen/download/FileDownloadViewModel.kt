package se.joynes.terminalhub.ui.screen.download

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import se.joynes.terminalhub.data.repository.ServerRepository
import se.joynes.terminalhub.data.security.SecurePrefsManager
import se.joynes.terminalhub.data.ssh.RemoteFileEntry
import se.joynes.terminalhub.data.ssh.ScpDownloader
import javax.inject.Inject

sealed interface DownloadState {
    object Idle : DownloadState
    object LoadingList : DownloadState
    data class Listed(val files: List<RemoteFileEntry>) : DownloadState
    data class Downloading(val fileName: String, val progress: Float) : DownloadState
    data class Done(val fileName: String, val bytes: Long) : DownloadState
    data class Error(val message: String) : DownloadState
}

@HiltViewModel
class FileDownloadViewModel @Inject constructor(
    private val serverRepo: ServerRepository,
    private val scpDownloader: ScpDownloader,
    private val securePrefs: SecurePrefsManager
) : ViewModel() {

    private val states = MutableStateFlow<Map<Long, DownloadState>>(emptyMap())

    fun downloadState(projectId: Long): Flow<DownloadState> =
        states.map { it[projectId] ?: DownloadState.Idle }

    fun reset(projectId: Long) {
        states.update { it - projectId }
    }

    fun loadRemoteFiles(serverId: Long, projectId: Long) {
        val current = states.value[projectId]
        if (current is DownloadState.LoadingList || current is DownloadState.Downloading) return
        viewModelScope.launch {
            setState(projectId, DownloadState.LoadingList)
            try {
                val server = serverRepo.getById(serverId) ?: error("Server not found")
                val files = scpDownloader.listFiles(
                    server = server,
                    password = securePrefs.getPassword(server.id),
                    privateKeyPem = securePrefs.getPrivateKey(server.id),
                    remoteDir = SSH_LOGIN_DIRECTORY
                )
                setState(projectId, DownloadState.Listed(files))
            } catch (e: Exception) {
                setState(projectId, DownloadState.Error(e.message ?: "Could not list remote files"))
            }
        }
    }

    fun startDownload(serverId: Long, projectId: Long, fileName: String, uri: Uri, context: Context) {
        if (states.value[projectId] is DownloadState.Downloading) return
        viewModelScope.launch {
            try {
                val server = serverRepo.getById(serverId) ?: error("Server not found")
                val output = context.contentResolver.openOutputStream(uri) ?: error("Cannot open destination")

                setState(projectId, DownloadState.Downloading(fileName, 0f))
                var bytes = 0L
                scpDownloader.download(
                    server = server,
                    password = securePrefs.getPassword(server.id),
                    privateKeyPem = securePrefs.getPrivateKey(server.id),
                    remoteDir = SSH_LOGIN_DIRECTORY,
                    fileName = fileName,
                    outputStream = output
                ).collect { progress ->
                    bytes = progress.bytesTransferred
                    setState(projectId, DownloadState.Downloading(progress.fileName, progress.percent / 100f))
                }
                setState(projectId, DownloadState.Done(fileName, bytes))
            } catch (e: Exception) {
                setState(projectId, DownloadState.Error(e.message ?: "Download failed"))
            }
        }
    }

    private fun setState(projectId: Long, state: DownloadState) {
        states.update { it + (projectId to state) }
    }

    companion object {
        private const val SSH_LOGIN_DIRECTORY = "."
    }
}
