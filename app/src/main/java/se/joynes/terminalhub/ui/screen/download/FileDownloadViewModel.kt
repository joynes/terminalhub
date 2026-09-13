package se.joynes.terminalhub.ui.screen.download

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
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
    data class PreviewLoading(
        val directory: String,
        val entries: List<RemoteFileEntry>,
        val fileName: String,
        val progress: Float = 0f
    ) : DownloadState
    data class PreviewReady(
        val directory: String,
        val entries: List<RemoteFileEntry>,
        val fileName: String,
        val content: String,
        val markdown: Boolean
    ) : DownloadState
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
    private val previewJobs = mutableMapOf<Long, Job>()

    fun downloadState(projectId: Long): Flow<DownloadState> =
        states.map { it[projectId] ?: DownloadState.Idle }

    fun reset(projectId: Long) {
        previewJobs.remove(projectId)?.cancel()
        states.update { it - projectId }
    }

    fun loadRemoteFiles(serverId: Long, projectId: Long, relativeDirectory: String = "") {
        val current = states.value[projectId]
        if (current is DownloadState.LoadingList ||
            current is DownloadState.Downloading ||
            current is DownloadState.PreviewLoading
        ) return
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

    fun previewRemoteFile(
        serverId: Long,
        projectId: Long,
        entry: RemoteFileEntry
    ) {
        val listed = states.value[projectId] as? DownloadState.Listed ?: return
        if (!isPreviewableTextFile(entry.name) || entry.isDirectory) return
        if (entry.size > MAX_REMOTE_TEXT_PREVIEW_BYTES) {
            setState(
                projectId,
                DownloadState.Error(
                    "Preview supports text files up to ${MAX_REMOTE_TEXT_PREVIEW_BYTES / 1024} KB.",
                    listed.directory
                )
            )
            return
        }

        previewJobs[projectId] = viewModelScope.launch {
            val output = LimitedPreviewOutputStream(MAX_REMOTE_TEXT_PREVIEW_BYTES.toInt())
            try {
                val (server, project) = resolveRemoteProject(serverId, projectId)
                val directory = remoteSubdirectory(engine.projectPath(server, project), listed.directory)
                setState(
                    projectId,
                    DownloadState.PreviewLoading(listed.directory, listed.entries, entry.name)
                )
                scpDownloader.download(
                    server = server,
                    password = securePrefs.getPassword(server.id),
                    privateKeyPem = securePrefs.getPrivateKey(server.id),
                    remoteDir = directory,
                    fileName = entry.name,
                    outputStream = output
                ).collect { progress ->
                    setState(
                        projectId,
                        DownloadState.PreviewLoading(
                            listed.directory,
                            listed.entries,
                            entry.name,
                            progress.percent / 100f
                        )
                    )
                }
                val bytes = output.toByteArray()
                check(bytes.none { it == 0.toByte() }) { "This file appears to be binary and cannot be previewed." }
                val content = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
                setState(
                    projectId,
                    DownloadState.PreviewReady(
                        directory = listed.directory,
                        entries = listed.entries,
                        fileName = entry.name,
                        content = content,
                        markdown = isMarkdownFile(entry.name)
                    )
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                setState(
                    projectId,
                    DownloadState.Error(error.message ?: "Could not preview file", listed.directory)
                )
            } finally {
                previewJobs.remove(projectId)
            }
        }
    }

    fun closePreview(projectId: Long) {
        val current = states.value[projectId]
        if (current is DownloadState.PreviewReady) {
            setState(projectId, DownloadState.Listed(current.directory, current.entries))
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

private class LimitedPreviewOutputStream(
    private val maximumBytes: Int
) : ByteArrayOutputStream() {
    override fun write(value: Int) {
        ensureCapacityFor(1)
        super.write(value)
    }

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        ensureCapacityFor(length)
        super.write(buffer, offset, length)
    }

    private fun ensureCapacityFor(additionalBytes: Int) {
        if (count + additionalBytes > maximumBytes) {
            throw IOException("Preview supports text files up to ${maximumBytes / 1024} KB.")
        }
    }
}
