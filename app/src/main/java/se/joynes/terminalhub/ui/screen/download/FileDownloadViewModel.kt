package se.joynes.terminalhub.ui.screen.download

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import se.joynes.terminalhub.data.model.Project
import se.joynes.terminalhub.data.model.Server
import se.joynes.terminalhub.data.repository.ProjectRepository
import se.joynes.terminalhub.data.repository.ServerRepository
import se.joynes.terminalhub.data.security.SecurePrefsManager
import se.joynes.terminalhub.data.ssh.RemoteFileEntry
import se.joynes.terminalhub.data.ssh.ScpDownloader
import se.joynes.terminalhub.data.ssh.remoteSubdirectory
import se.joynes.terminalhub.domain.ScriptTemplateEngine
import java.io.File
import javax.inject.Inject

sealed interface DownloadState {
    object Idle : DownloadState
    data class LoadingList(val directory: String) : DownloadState
    data class Listed(val directory: String, val entries: List<RemoteFileEntry>) : DownloadState
    data class Downloading(
        val fileName: String,
        val progress: Float,
        val currentFile: Int = 1,
        val totalFiles: Int = 1
    ) : DownloadState
    data class Done(val fileName: String, val bytes: Long, val uri: Uri) : DownloadState
    data class BatchDone(val files: List<DownloadedRemoteFile>) : DownloadState
    data class Error(val message: String, val directory: String = "") : DownloadState
}

data class DownloadedRemoteFile(val fileName: String, val bytes: Long, val uri: Uri)

@HiltViewModel
class FileDownloadViewModel @Inject constructor(
    private val serverRepo: ServerRepository,
    private val projectRepo: ProjectRepository,
    private val engine: ScriptTemplateEngine,
    private val scpDownloader: ScpDownloader,
    private val securePrefs: SecurePrefsManager
) : ViewModel() {

    private val states = MutableStateFlow<Map<Long, DownloadState>>(emptyMap())

    fun downloadState(projectId: Long): Flow<DownloadState> =
        states.map { it[projectId] ?: DownloadState.Idle }

    fun reset(projectId: Long) {
        states.update { it - projectId }
    }

    fun loadRemoteFiles(serverId: Long, projectId: Long, relativeDirectory: String = "") {
        val current = states.value[projectId]
        if (current is DownloadState.LoadingList || current is DownloadState.Downloading) return
        viewModelScope.launch {
            setState(projectId, DownloadState.LoadingList(relativeDirectory))
            try {
                val (server, project) = resolveRemoteProject(serverId, projectId)
                val directory = remoteSubdirectory(engine.projectPath(server, project), relativeDirectory)
                val entries = scpDownloader.listFiles(
                    server = server,
                    password = securePrefs.getPassword(server.id),
                    privateKeyPem = securePrefs.getPrivateKey(server.id),
                    remoteDir = directory
                )
                setState(projectId, DownloadState.Listed(relativeDirectory, entries))
            } catch (e: Exception) {
                setState(
                    projectId,
                    DownloadState.Error(e.message ?: "Could not list remote files", relativeDirectory)
                )
            }
        }
    }

    fun startDownload(
        serverId: Long,
        projectId: Long,
        relativeDirectory: String,
        fileName: String,
        uri: Uri,
        context: Context
    ) {
        if (states.value[projectId] is DownloadState.Downloading) return
        viewModelScope.launch {
            var temporaryFile: File? = null
            try {
                val (server, project) = resolveRemoteProject(serverId, projectId)
                val directory = remoteSubdirectory(engine.projectPath(server, project), relativeDirectory)
                val transferDirectory = File(context.cacheDir, "downloads").also { directory ->
                    check(directory.exists() || directory.mkdirs()) { "Cannot prepare download" }
                }
                val stagedDownload = File.createTempFile("terminalhub-", ".part", transferDirectory)
                temporaryFile = stagedDownload

                setState(projectId, DownloadState.Downloading(fileName, 0f))
                var bytes = 0L
                scpDownloader.download(
                    server = server,
                    password = securePrefs.getPassword(server.id),
                    privateKeyPem = securePrefs.getPrivateKey(server.id),
                    remoteDir = directory,
                    fileName = fileName,
                    outputStream = stagedDownload.outputStream()
                ).collect { progress ->
                    bytes = progress.bytesTransferred
                    setState(projectId, DownloadState.Downloading(progress.fileName, progress.percent / 100f))
                }
                val output = context.contentResolver.openOutputStream(uri) ?: error("Cannot open destination")
                output.use { destination ->
                    stagedDownload.inputStream().use { source -> source.copyTo(destination) }
                }
                setState(projectId, DownloadState.Done(fileName, bytes, uri))
            } catch (e: Exception) {
                setState(projectId, DownloadState.Error(e.message ?: "Download failed"))
            } finally {
                temporaryFile?.delete()
            }
        }
    }

    fun startDownloads(
        serverId: Long,
        projectId: Long,
        relativeDirectory: String,
        fileNames: List<String>,
        context: Context,
        createDestination: (String) -> Uri
    ) {
        if (fileNames.isEmpty() || states.value[projectId] is DownloadState.Downloading) return
        viewModelScope.launch {
            val completed = mutableListOf<DownloadedRemoteFile>()
            try {
                val (server, project) = resolveRemoteProject(serverId, projectId)
                val directory = remoteSubdirectory(engine.projectPath(server, project), relativeDirectory)
                val transferDirectory = File(context.cacheDir, "downloads").also { cacheDirectory ->
                    check(cacheDirectory.exists() || cacheDirectory.mkdirs()) { "Cannot prepare download" }
                }

                fileNames.forEachIndexed { index, fileName ->
                    var temporaryFile: File? = null
                    try {
                        val stagedDownload = File.createTempFile("terminalhub-", ".part", transferDirectory)
                        temporaryFile = stagedDownload
                        setState(
                            projectId,
                            DownloadState.Downloading(fileName, 0f, index + 1, fileNames.size)
                        )
                        var bytes = 0L
                        scpDownloader.download(
                            server = server,
                            password = securePrefs.getPassword(server.id),
                            privateKeyPem = securePrefs.getPrivateKey(server.id),
                            remoteDir = directory,
                            fileName = fileName,
                            outputStream = stagedDownload.outputStream()
                        ).collect { progress ->
                            bytes = progress.bytesTransferred
                            setState(
                                projectId,
                                DownloadState.Downloading(
                                    progress.fileName,
                                    progress.percent / 100f,
                                    index + 1,
                                    fileNames.size
                                )
                            )
                        }
                        val uri = withContext(Dispatchers.IO) { createDestination(fileName) }
                        withContext(Dispatchers.IO) {
                            val output = context.contentResolver.openOutputStream(uri)
                                ?: error("Cannot open destination for $fileName")
                            output.use { destination ->
                                stagedDownload.inputStream().use { source -> source.copyTo(destination) }
                            }
                        }
                        completed += DownloadedRemoteFile(fileName, bytes, uri)
                    } finally {
                        temporaryFile?.delete()
                    }
                }
                setState(projectId, DownloadState.BatchDone(completed))
            } catch (e: Exception) {
                val completedPrefix = if (completed.isEmpty()) "" else "${completed.size} file(s) downloaded. "
                setState(
                    projectId,
                    DownloadState.Error(
                        completedPrefix + (e.message ?: "Download failed"),
                        relativeDirectory
                    )
                )
            }
        }
    }

    private fun setState(projectId: Long, state: DownloadState) {
        states.update { it + (projectId to state) }
    }

    private suspend fun resolveRemoteProject(serverId: Long, projectId: Long): Pair<Server, Project> {
        val server = serverRepo.getById(serverId) ?: error("Server not found")
        val project = projectRepo.getById(projectId) ?: error("Project not found")
        check(!project.isLocal) { "Remote download is only available for SSH projects" }
        check(project.serverId == server.id) { "Project does not belong to this server" }
        return server to project
    }
}
