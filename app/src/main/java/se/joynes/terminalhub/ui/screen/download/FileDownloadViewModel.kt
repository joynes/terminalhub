package se.joynes.terminalhub.ui.screen.download

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import se.joynes.terminalhub.data.model.Project
import se.joynes.terminalhub.data.model.Server
import se.joynes.terminalhub.data.repository.ProjectRepository
import se.joynes.terminalhub.data.repository.ServerRepository
import se.joynes.terminalhub.data.security.SecurePrefsManager
import se.joynes.terminalhub.data.settings.AppSettingsRepository
import se.joynes.terminalhub.data.settings.RemoteBrowseLocationStore
import se.joynes.terminalhub.data.settings.RemoteBrowseViewState
import se.joynes.terminalhub.data.ssh.RemoteFileEntry
import se.joynes.terminalhub.data.ssh.ScpDownloader
import se.joynes.terminalhub.data.ssh.StagedRemoteDownload
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
        val totalFiles: Int = 1,
        val estimate: TransferEstimate = TransferEstimate(),
        val queuedFiles: Int = 0
    ) : DownloadState
    data class Paused(val fileName: String, val message: String, val completedFiles: Int) : DownloadState
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
        val markdown: Boolean,
        val imageBytes: ByteArray? = null,
        val pdfBytes: ByteArray? = null
    ) : DownloadState
    data class Error(val message: String, val directory: String = "", val previousListing: Listed? = null) : DownloadState
}

data class DownloadedRemoteFile(val fileName: String, val bytes: Long, val uri: Uri)

@HiltViewModel
class FileDownloadViewModel @Inject constructor(
    private val serverRepo: ServerRepository,
    private val projectRepo: ProjectRepository,
    private val engine: ScriptTemplateEngine,
    private val scpDownloader: ScpDownloader,
    private val securePrefs: SecurePrefsManager,
    private val settingsRepository: AppSettingsRepository,
    private val browseLocationStore: RemoteBrowseLocationStore
) : ViewModel() {

    private val states = MutableStateFlow<Map<Long, DownloadState>>(emptyMap())
    val activities = states.asStateFlow()
    private data class Batch(
        val serverId: Long, val directory: String, val names: List<String>, val context: Context,
        val destination: (String) -> Uri,
        var index: Int = 0,
        val completed: MutableList<DownloadedRemoteFile> = mutableListOf(),
        var staging: StagedRemoteDownload? = null
    )
    private val batches = mutableMapOf<Long, java.util.ArrayDeque<Batch>>()
    private val downloadJobs = mutableMapOf<Long, Job>()
    private val previewJobs = mutableMapOf<Long, Job>()
    private val listingJobs = mutableMapOf<Long, Job>()
    private val browseIdentities = mutableMapOf<Long, String>()

    internal fun browseIdentity(projectId: Long): String? = browseIdentities[projectId]
    internal fun readBrowseView(identity: String, directory: String) = browseLocationStore.readView(identity, directory)
    internal fun saveBrowseView(identity: String, directory: String, state: RemoteBrowseViewState) =
        browseLocationStore.saveView(identity, directory, state)

    val settings = settingsRepository.settings
    internal fun setSort(selection: RemoteFileSortSelection) =
        settingsRepository.setRemoteDownloadSort(selection.property.name, selection.ascending)

    fun downloadState(projectId: Long): Flow<DownloadState> =
        states.map { it[projectId] ?: DownloadState.Idle }

    fun reset(projectId: Long) {
        // Hiding the browser must not orphan a running/paused transfer or discard its checkpoint.
        if (batches[projectId]?.isNotEmpty() == true) return
        listingJobs.remove(projectId)?.cancel()
        previewJobs.remove(projectId)?.cancel()
        states.update { it - projectId }
    }

    fun loadRemoteFiles(serverId: Long, projectId: Long, relativeDirectory: String? = null) {
        val current = states.value[projectId]
        if (current is DownloadState.LoadingList ||
            current is DownloadState.Downloading || current is DownloadState.Paused ||
            current is DownloadState.PreviewLoading
        ) return
        var requestedDirectory = relativeDirectory ?: ""
        setState(projectId, DownloadState.LoadingList(requestedDirectory))
        listingJobs[projectId] = viewModelScope.launch {
            try {
                val (server, project) = resolveRemoteProject(serverId, projectId)
                val projectRoot = engine.projectPath(server, project)
                requestedDirectory = relativeDirectory
                    ?: browseLocationStore.lastDirectory(server, project, projectRoot) ?: ""
                setState(projectId, DownloadState.LoadingList(requestedDirectory))
                val directory = remoteSubdirectory(projectRoot, requestedDirectory)
                val entries = scpDownloader.listFiles(
                    server = server,
                    password = securePrefs.getPassword(server.id),
                    privateKeyPem = securePrefs.getPrivateKey(server.id),
                    remoteDir = directory
                )
                currentCoroutineContext().ensureActive()
                browseLocationStore.save(server, project, projectRoot, requestedDirectory)
                browseIdentities[projectId] = browseLocationStore.locationKey(server, project, projectRoot)
                setState(projectId, DownloadState.Listed(requestedDirectory, entries))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                setState(
                    projectId,
                    DownloadState.Error(e.message ?: "Could not list remote files", requestedDirectory)
                )
            } finally {
                if (listingJobs[projectId] === currentCoroutineContext()[Job]) listingJobs.remove(projectId)
            }
        }
    }

    fun previewRemoteFile(
        serverId: Long,
        projectId: Long,
        entry: RemoteFileEntry
    ) {
        val listed = states.value[projectId] as? DownloadState.Listed ?: return
        val image = isPreviewableImageFile(entry.name)
        val pdf = isPreviewablePdfFile(entry.name)
        if (!isRemotePreviewSupported(entry.name) || entry.isDirectory) return
        val limit = when { pdf -> MAX_REMOTE_PDF_PREVIEW_BYTES; image -> MAX_REMOTE_IMAGE_PREVIEW_BYTES; else -> MAX_REMOTE_TEXT_PREVIEW_BYTES }
        if (entry.size > limit) {
            setState(
                projectId,
                DownloadState.Error(
                    "Preview supports ${if (pdf) "PDF files" else if (image) "images" else "text files"} up to ${limit / 1024} KB.",
                    listed.directory, listed
                )
            )
            return
        }

        setState(projectId, DownloadState.PreviewLoading(listed.directory, listed.entries, entry.name))
        previewJobs[projectId] = viewModelScope.launch {
            val output = LimitedPreviewOutputStream(limit.toInt())
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
                currentCoroutineContext().ensureActive()
                val bytes = output.toByteArray()
                if (!image && !pdf) check(bytes.none { it == 0.toByte() }) { "This file appears to be binary and cannot be previewed." }
                val content = if (image || pdf) "" else bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
                setState(
                    projectId,
                    DownloadState.PreviewReady(
                        directory = listed.directory,
                        entries = listed.entries,
                        fileName = entry.name,
                        content = content,
                        markdown = isMarkdownFile(entry.name),
                        imageBytes = if (image) bytes else null,
                        pdfBytes = if (pdf) bytes else null
                    )
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                setState(
                    projectId,
                    DownloadState.Error(error.message ?: "Could not preview file", listed.directory, listed)
                )
            } finally {
                if (previewJobs[projectId] === currentCoroutineContext()[Job]) previewJobs.remove(projectId)
            }
        }
    }

    fun closePreview(projectId: Long) {
        val current = states.value[projectId]
        val listing = when (current) {
            is DownloadState.PreviewReady -> DownloadState.Listed(current.directory, current.entries)
            is DownloadState.PreviewLoading -> DownloadState.Listed(current.directory, current.entries)
            is DownloadState.Error -> current.previousListing
            else -> null
        } ?: return
        previewJobs.remove(projectId)?.cancel()
        setState(projectId, listing)
    }

    fun startDownloads(
        serverId: Long,
        projectId: Long,
        relativeDirectory: String,
        fileNames: List<String>,
        context: Context,
        createDestination: (String) -> Uri
    ) {
        if (fileNames.isEmpty()) return
        val queue = batches.getOrPut(projectId) { java.util.ArrayDeque() }
        val names = fileNames.distinct()
        // Double taps must not enqueue the exact same request twice.
        if (queue.any { it.serverId == serverId && it.directory == relativeDirectory && it.names == names }) return
        queue.add(Batch(serverId, relativeDirectory, names, context.applicationContext ?: context, createDestination))
        runDownloads(projectId)
    }

    fun retryDownloads(projectId: Long) = runDownloads(projectId)

    fun cancelDownloads(projectId: Long) {
        downloadJobs[projectId]?.cancel()
        batches[projectId]?.firstOrNull { it.index < it.names.size }?.staging?.cancel()
        // Actual cleanup happens when the worker unwinds, after its output stream is closed.
        if (downloadJobs[projectId] == null) clearDownloads(projectId)
    }

    override fun onCleared() {
        batches.values.forEach { queue -> queue.firstOrNull { it.index < it.names.size }?.staging?.cancel() }
        super.onCleared()
    }

    private fun clearDownloads(projectId: Long) {
        batches.remove(projectId)?.forEach { it.staging?.file?.delete() }
        setState(projectId, DownloadState.Idle)
    }

    private fun runDownloads(projectId: Long) {
        val queue = batches[projectId] ?: return
        if (downloadJobs[projectId]?.isActive == true || queue.isEmpty()) return
        val first = queue.firstOrNull { it.index < it.names.size } ?: return
        setState(projectId, DownloadState.Downloading(first.names[first.index], 0f))
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                while (true) {
                    val batch = queue.firstOrNull { it.index < it.names.size } ?: break
                    val (server, project) = resolveRemoteProject(batch.serverId, projectId)
                    val directory = remoteSubdirectory(engine.projectPath(server, project), batch.directory)
                    val transferDirectory = File(batch.context.cacheDir, "downloads").also { cacheDirectory ->
                        check(cacheDirectory.exists() || cacheDirectory.mkdirs()) { "Cannot prepare download" }
                    }

                    while (batch.index < batch.names.size) {
                        val fileName = batch.names[batch.index]
                        val staging = batch.staging ?: StagedRemoteDownload(
                            File.createTempFile("terminalhub-", ".part", transferDirectory)
                        ).also { batch.staging = it }
                        setState(
                            projectId,
                            DownloadState.Downloading(fileName, 0f, batch.index + 1, batch.names.size)
                        )
                        var bytes = 0L
                        val rate = TransferRate()
                        scpDownloader.downloadToFile(
                            server = server,
                            password = securePrefs.getPassword(server.id),
                            privateKeyPem = securePrefs.getPrivateKey(server.id),
                            remoteDir = directory,
                            fileName = fileName,
                            staging = staging
                        ).collect { progress ->
                            bytes = progress.bytesTransferred
                            setState(
                                projectId,
                                DownloadState.Downloading(
                                    progress.fileName,
                                    progress.percent / 100f,
                                    batch.index + 1,
                                    batch.names.size,
                                    rate.sample(bytes, progress.totalBytes),
                                    queue.sumOf { it.names.size - it.index } - 1
                                )
                            )
                        }
                        currentCoroutineContext().ensureActive()
                        val uri = withContext(Dispatchers.IO) { batch.destination(fileName) }
                        withContext(Dispatchers.IO) {
                            val output = batch.context.contentResolver.openOutputStream(uri)
                                ?: error("Cannot open destination for $fileName")
                            output.use { destination ->
                                staging.file.inputStream().use { source -> source.copyTo(destination) }
                            }
                        }
                        batch.completed += DownloadedRemoteFile(fileName, bytes, uri)
                        staging.file.delete()
                        batch.staging = null
                        batch.index++
                    }
                }
                batches.remove(projectId)
                setState(projectId, DownloadState.BatchDone(queue.flatMap { it.completed }))
            } catch (cancelled: CancellationException) {
                clearDownloads(projectId)
                throw cancelled
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                val batch = queue.firstOrNull { it.index < it.names.size }
                setState(
                    projectId,
                    DownloadState.Paused(
                        batch?.names?.getOrNull(batch.index).orEmpty(),
                        e.message ?: "Download failed",
                        queue.sumOf { it.completed.size }
                    )
                )
            } finally {
                downloadJobs.remove(projectId)
            }
        }
        downloadJobs[projectId] = job
        job.start()
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
            throw IOException("Preview supports files up to ${maximumBytes / 1024} KB.")
        }
    }
}
