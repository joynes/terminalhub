package se.joynes.terminalhub.ui.screen.download

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.joynes.terminalhub.data.ssh.RemoteFileEntry
import se.joynes.terminalhub.ui.components.PixelProgressBar
import se.joynes.terminalhub.ui.components.RetroButton
import se.joynes.terminalhub.ui.theme.MegaDriveAccent
import se.joynes.terminalhub.ui.theme.MegaDriveBg
import se.joynes.terminalhub.ui.theme.MegaDriveDim
import se.joynes.terminalhub.ui.theme.MegaDrivePrimary
import se.joynes.terminalhub.ui.theme.MonoFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FloatingFileDownloadDialog(
    viewModel: FileDownloadViewModel,
    projectId: Long,
    serverId: Long,
    downloadState: DownloadState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val destinationDirectoryStore = remember(context) { RemoteDownloadDirectoryStore(context) }
    val settings by viewModel.settings.collectAsState()
    val sortSelection = RemoteFileSortSelection(RemoteFileSort.valueOf(settings.remoteDownloadSort), settings.remoteDownloadSortAscending)
    val listed = when (downloadState) {
        is DownloadState.Listed -> downloadState
        is DownloadState.PreviewLoading -> DownloadState.Listed(downloadState.directory, downloadState.entries)
        is DownloadState.PreviewReady -> DownloadState.Listed(downloadState.directory, downloadState.entries)
        is DownloadState.Error -> downloadState.previousListing
        else -> null
    }
    val sortedEntries = listed?.let { sortRemoteFileEntries(it.entries, sortSelection) }.orEmpty()
    val identity = viewModel.browseIdentity(projectId)
    val browserState = if (listed != null) rememberRemoteBrowserUiState(
        identity = identity ?: "project:$projectId",
        directory = listed.directory,
        entries = sortedEntries,
        load = { identity?.let { viewModel.readBrowseView(it, listed.directory) }
            ?: se.joynes.terminalhub.data.settings.RemoteBrowseViewState() },
        save = { state -> identity?.let { viewModel.saveBrowseView(it, listed.directory, state) } }
    ) else null
    val selectedFileNames = browserState?.selectedFiles.orEmpty()
    val scope = rememberCoroutineScope()
    var pendingDownload by remember { mutableStateOf<PendingFolderDownload?>(null) }
    var destination by remember { mutableStateOf(destinationDirectoryStore.lastPersistedDirectory()) }
    var checkingDestination by remember { mutableStateOf(false) }
    var destinationMessage by remember { mutableStateOf<String?>(null) }

    fun downloadTo(request: PendingFolderDownload, uri: Uri) {
        viewModel.startDownloads(request.serverId, request.projectId, request.directory,
            request.fileNames, context) { fileName ->
            try {
                createDownloadDocument(context, uri, fileName)
            } catch (error: Exception) {
                destinationDirectoryStore.clear()
                throw java.io.IOException("Cannot write to the download folder. Return to the file list and use CHANGE FOLDER. ${error.message.orEmpty()}", error)
            }
        }
    }

    val destinationPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { directoryUri ->
        val request = pendingDownload
        pendingDownload = null
        if (directoryUri != null) {
            val permissionPersisted = runCatching {
                context.contentResolver.takePersistableUriPermission(
                    directoryUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }.isSuccess
            if (permissionPersisted) {
                destinationDirectoryStore.save(directoryUri)
                destination = directoryUri
                destinationMessage = null
            } else {
                destinationDirectoryStore.clear()
                destination = null
                destinationMessage = "Folder access could not be remembered. Choose a folder again for the next download."
            }
            if (request != null) downloadTo(request, directoryUri)
        }
    }

    LaunchedEffect(projectId, downloadState is DownloadState.Idle) {
        if (downloadState is DownloadState.Idle) {
            viewModel.loadRemoteFiles(serverId, projectId)
        }
    }

    val folderController = RemoteDownloadFolderController(
        remembered = { destinationDirectoryStore.lastPersistedDirectory() },
        isWritable = { uri -> withContext(Dispatchers.IO) { destinationDirectoryStore.isWritableDirectory(uri) } },
        forget = {
            destinationDirectoryStore.clear()
            if (destination != null) destinationMessage = "The previous folder is no longer available. Choose a download folder again."
            destination = null
        },
        start = ::downloadTo,
        choose = { request, initial ->
            pendingDownload = request
            destinationPicker.launch(initial?.let {
                DocumentsContract.buildDocumentUriUsingTree(it, DocumentsContract.getTreeDocumentId(it))
            })
        }
    )

    if (downloadState is DownloadState.PreviewReady) {
        if (downloadState.pdfBytes != null) {
            RemotePdfPreviewDialog(downloadState.fileName, downloadState.pdfBytes) { viewModel.closePreview(projectId) }
            return
        }
        if (downloadState.imageBytes != null) {
            RemoteImagePreviewDialog(downloadState.fileName, downloadState.imageBytes) { viewModel.closePreview(projectId) }
            return
        }
        RemoteTextPreviewDialog(
            fileName = downloadState.fileName,
            content = downloadState.content,
            markdown = downloadState.markdown,
            onDismiss = { viewModel.closePreview(projectId) }
        )
        return
    }

    fun close() { viewModel.reset(projectId); onDismiss() }
    fun back() {
        when (downloadState) {
            is DownloadState.PreviewLoading -> viewModel.closePreview(projectId)
            is DownloadState.Error -> if (downloadState.previousListing != null) viewModel.closePreview(projectId)
                else if (downloadState.directory.isNotEmpty()) viewModel.loadRemoteFiles(serverId, projectId, parentRemoteDirectory(downloadState.directory)) else close()
            is DownloadState.Listed -> if (downloadState.directory.isNotEmpty()) viewModel.loadRemoteFiles(serverId, projectId, parentRemoteDirectory(downloadState.directory)) else close()
            else -> close()
        }
    }
    RemoteBrowserFrame("Remote files", onBack = ::back, onClose = ::close) {
        if (downloadState is DownloadState.Listed) {
            RemoteBrowserListing(downloadState.directory, sortedEntries, browserState!!, sortSelection,
                onSort = { viewModel.setSort(nextRemoteFileSortSelection(sortSelection, it)) },
                onNavigate = { viewModel.loadRemoteFiles(serverId, projectId, it) },
                onRefresh = { viewModel.loadRemoteFiles(serverId, projectId, downloadState.directory) },
                onPreview = { viewModel.previewRemoteFile(serverId, projectId, it) },
                destination = destination?.let { "Save to: " + runCatching { DocumentsContract.getTreeDocumentId(it).substringAfterLast('/') }.getOrDefault(it.toString()) }
                    ?: "Choose a folder when downloading",
                destinationMessage = destinationMessage, checkingDestination = checkingDestination,
                onChangeDestination = { folderController.changeFolder() },
                onDownload = {
                    if (selectedFileNames.isNotEmpty() && !checkingDestination) {
                        val request = PendingFolderDownload(serverId, projectId, downloadState.directory, selectedFileNames.toList())
                        checkingDestination = true
                        scope.launch { try { folderController.download(request) } finally { checkingDestination = false } }
                    }
                })
        } else Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (downloadState) {
                is DownloadState.Idle, is DownloadState.LoadingList -> {
                    androidx.compose.material3.CircularProgressIndicator()
                    Text("Loading remote files…", color = MegaDrivePrimary)
                }
                is DownloadState.Listed -> Unit
                is DownloadState.Downloading -> {
                    Text(
                        "FILE ${downloadState.currentFile} OF ${downloadState.totalFiles}: ${downloadState.fileName}",
                        color = MegaDrivePrimary,
                        fontSize = 11.sp,
                        fontFamily = MonoFontFamily,
                        maxLines = 2
                    )
                    PixelProgressBar(
                        progress = downloadState.progress,
                        label = "DOWNLOADING...",
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(downloadState.estimate.label(), color = MegaDrivePrimary, fontSize = 12.sp)
                    if (downloadState.queuedFiles > 0) Text("${downloadState.queuedFiles} file(s) waiting",
                        color = MegaDrivePrimary, fontSize = 12.sp)
                    Text("You can close this panel and keep working.", color = MegaDrivePrimary, fontSize = 12.sp)
                    RetroButton("CANCEL QUEUE", onClick = { viewModel.cancelDownloads(projectId) })
                }
                is DownloadState.Paused -> {
                    Text("TRANSFER PAUSED: ${downloadState.fileName}", color = MegaDrivePrimary, fontSize = 12.sp)
                    Text(downloadState.message, color = MegaDrivePrimary, fontSize = 12.sp)
                    Text("Retry resumes partial files when the server's size and modification time still match. Otherwise it restarts the file.",
                        color = MegaDrivePrimary, fontSize = 12.sp)
                    RetroButton("RETRY QUEUE", onClick = { viewModel.retryDownloads(projectId) })
                    RetroButton("CANCEL QUEUE", onClick = { viewModel.cancelDownloads(projectId) })
                }
                is DownloadState.PreviewLoading -> {
                    Text(
                        "PREPARING PREVIEW: ${downloadState.fileName}",
                        color = MegaDrivePrimary,
                        fontSize = 11.sp,
                        fontFamily = MonoFontFamily,
                        maxLines = 2
                    )
                    PixelProgressBar(
                        progress = downloadState.progress,
                        label = "LOADING PREVIEW...",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                is DownloadState.PreviewReady -> Unit
                is DownloadState.BatchDone -> {
                    Text(
                        "DONE - ${downloadState.files.size} FILE(S)",
                        color = MegaDrivePrimary,
                        fontSize = 11.sp,
                        fontFamily = MonoFontFamily
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(downloadState.files, key = { it.uri.toString() }) { file ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MegaDriveBg, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    file.fileName,
                                    color = MegaDrivePrimary,
                                    fontSize = 10.sp,
                                    fontFamily = MonoFontFamily,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "OPEN",
                                    color = MegaDriveAccent,
                                    fontSize = 10.sp,
                                    fontFamily = MonoFontFamily,
                                    modifier = Modifier
                                        .clickable {
                                            openDownloadedFile(context, file.fileName, file.uri)
                                        }
                                        .padding(6.dp)
                                )
                            }
                        }
                    }
                    RetroButton(
                        text = "CLOSE",
                        onClick = { viewModel.reset(projectId); onDismiss() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                is DownloadState.Error -> {
                    if (downloadState.previousListing != null) RetroButton("BACK TO FILES", onClick = { viewModel.closePreview(projectId) })
                    Text(
                        "ERROR: ${downloadState.message}",
                        color = MegaDriveAccent,
                        fontSize = 11.sp,
                        fontFamily = MonoFontFamily
                    )
                    RetroButton(
                        text = "TRY AGAIN",
                        onClick = {
                            viewModel.loadRemoteFiles(serverId, projectId, downloadState.directory)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (downloadState.directory.isNotEmpty()) {
                        RetroButton(
                            text = "PROJECT ROOT",
                            onClick = { viewModel.loadRemoteFiles(serverId, projectId, "") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

private fun openDownloadedFile(context: Context, fileName: String, uri: Uri) {
    val reportedType = context.contentResolver.getType(uri)
    val mimeType = reportedType
        ?.takeUnless { it.equals("application/octet-stream", ignoreCase = true) }
        ?: downloadedFileMimeType(fileName)
    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        clipData = ClipData.newUri(context.contentResolver, fileName, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(viewIntent, "Open $fileName").apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    runCatching { context.startActivity(chooser) }
        .onFailure {
            Toast.makeText(context, "No app can open this file", Toast.LENGTH_LONG).show()
        }
}

internal fun createDownloadDocument(context: Context, directoryUri: Uri, fileName: String): Uri {
    val parent = DocumentsContract.buildDocumentUriUsingTree(
        directoryUri,
        DocumentsContract.getTreeDocumentId(directoryUri)
    )
    val mimeType = downloadedFileMimeType(fileName).takeUnless { it == "*/*" }
        ?: "application/octet-stream"
    return DocumentsContract.createDocument(context.contentResolver, parent, mimeType, fileName)
        ?: error("Could not create $fileName in the selected folder")
}

internal fun downloadedFileMimeType(fileName: String): String {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    return when (extension) {
        "aac" -> "audio/aac"
        "flac" -> "audio/flac"
        "m4a" -> "audio/mp4"
        "mid", "midi" -> "audio/midi"
        "mp3" -> "audio/mpeg"
        "ogg", "oga" -> "audio/ogg"
        "wav" -> "audio/wav"
        "webm" -> "audio/webm"
        "avi" -> "video/x-msvideo"
        "m4v", "mp4" -> "video/mp4"
        "mov" -> "video/quicktime"
        "gif" -> "image/gif"
        "jpeg", "jpg" -> "image/jpeg"
        "png" -> "image/png"
        "svg" -> "image/svg+xml"
        "webp" -> "image/webp"
        "csv" -> "text/csv"
        "htm", "html" -> "text/html"
        "json" -> "application/json"
        "log", "md", "sh", "txt" -> "text/plain"
        "pdf" -> "application/pdf"
        "zip" -> "application/zip"
        else -> "*/*"
    }
}

@Composable
internal fun RemoteFileList(
    entries: List<RemoteFileEntry>,
    selectedFileNames: Set<String>,
    modifier: Modifier = Modifier,
    onOpenDirectory: (RemoteFileEntry) -> Unit,
    onToggleFile: (RemoteFileEntry) -> Unit,
    onPreviewFile: (RemoteFileEntry) -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    previewOnTap: Boolean = false
) {
    if (entries.isEmpty()) {
        androidx.compose.foundation.layout.Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("No files to show.", color = MegaDriveDim, fontSize = 14.sp)
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(entries, key = ::remoteEntryKey) { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MegaDriveBg, RoundedCornerShape(4.dp))
                    .clickable {
                        if (entry.isDirectory) onOpenDirectory(entry)
                        else if (previewOnTap && isRemotePreviewSupported(entry.name)) onPreviewFile(entry)
                        else onToggleFile(entry)
                    }
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(if (entry.isDirectory) BrowserFolderIcon else BrowserFileIcon,
                    contentDescription = if (entry.isDirectory) "Folder" else null,
                    tint = if (entry.isDirectory) MegaDrivePrimary else MegaDriveDim, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        entry.name,
                        color = MegaDrivePrimary,
                        fontSize = 14.sp,
                        fontFamily = MonoFontFamily,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    if (!entry.isDirectory) Text(formatBytes(entry.size), color = MegaDriveDim, fontSize = 11.sp)
                    if (entry.modifiedAtEpochSeconds > 0L) {
                        Text(
                            formatRemoteModifiedTime(entry.modifiedAtEpochSeconds),
                            color = MegaDriveDim,
                            fontSize = 11.sp,
                            fontFamily = MonoFontFamily,
                            maxLines = 1
                        )
                    }
                }
                if (entry.isDirectory) {
                    Text(">", color = MegaDrivePrimary, fontSize = 11.sp, fontFamily = MonoFontFamily)
                } else {
                    if (isRemotePreviewSupported(entry.name) && !previewOnTap) {
                        Text(
                            if (previewOnTap) "VIEW" else "PREVIEW",
                            color = MegaDriveAccent,
                            fontSize = 9.sp,
                            fontFamily = MonoFontFamily,
                            modifier = Modifier
                                .clickable { onPreviewFile(entry) }
                                .padding(horizontal = 7.dp, vertical = 6.dp)
                        )
                    }
                    Checkbox(
                        checked = entry.name in selectedFileNames,
                        onCheckedChange = { onToggleFile(entry) },
                        modifier = Modifier.semantics { contentDescription = "Select ${entry.name}" }
                    )
                }
            }
        }
    }
}

internal enum class RemoteFileSort(val label: String) {
    NAME("NAME"),
    DATE("DATE"),
    TYPE("TYPE"),
    SIZE("SIZE")
}

internal data class RemoteFileSortSelection(
    val property: RemoteFileSort,
    val ascending: Boolean
)

@Composable
internal fun RemoteDirectoryHeader(
    directory: String,
    onNavigateUp: () -> Unit
) {
    val canNavigateUp = directory.isNotEmpty()
    Text(
        text = if (canNavigateUp) "↑  ${remoteDirectoryLabel(directory)}" else remoteDirectoryLabel(directory),
        color = if (canNavigateUp) MegaDrivePrimary else MegaDriveDim,
        fontSize = 10.sp,
        fontFamily = MonoFontFamily,
        maxLines = 2,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = canNavigateUp, onClick = onNavigateUp)
            .padding(vertical = 6.dp)
    )
}

@Composable
internal fun RemoteFileSortControls(
    selection: RemoteFileSortSelection,
    onSelect: (RemoteFileSort) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "SORT",
            color = MegaDriveDim,
            fontSize = 9.sp,
            fontFamily = MonoFontFamily
        )
        RemoteFileSort.entries.forEach { property ->
            val selected = property == selection.property
            Text(
                text = buildString {
                    append(property.label)
                    if (selected) append(if (selection.ascending) " ↑" else " ↓")
                },
                color = if (selected) MegaDriveAccent else MegaDriveDim,
                fontSize = 9.sp,
                fontFamily = MonoFontFamily,
                modifier = Modifier
                    .background(MegaDriveBg, RoundedCornerShape(3.dp))
                    .clickable { onSelect(property) }
                    .padding(horizontal = 7.dp, vertical = 6.dp)
            )
        }
    }
}

internal fun nextRemoteFileSortSelection(
    current: RemoteFileSortSelection,
    selectedProperty: RemoteFileSort
): RemoteFileSortSelection = if (current.property == selectedProperty) {
    current.copy(ascending = !current.ascending)
} else {
    RemoteFileSortSelection(
        property = selectedProperty,
        ascending = selectedProperty == RemoteFileSort.NAME || selectedProperty == RemoteFileSort.TYPE
    )
}

internal fun sortRemoteFileEntries(
    entries: List<RemoteFileEntry>,
    selection: RemoteFileSortSelection
): List<RemoteFileEntry> {
    val propertyComparator = when (selection.property) {
        RemoteFileSort.NAME -> compareBy<RemoteFileEntry> { it.name.lowercase() }
        RemoteFileSort.DATE -> compareBy<RemoteFileEntry> { it.modifiedAtEpochSeconds }
            .thenBy { it.name.lowercase() }
        RemoteFileSort.TYPE -> compareBy<RemoteFileEntry> { remoteFileType(it.name) }
            .thenBy { it.name.lowercase() }
        RemoteFileSort.SIZE -> compareBy<RemoteFileEntry> { it.size }
            .thenBy { it.name.lowercase() }
    }
    val directedComparator = if (selection.ascending) propertyComparator else propertyComparator.reversed()
    return entries
        .groupBy(RemoteFileEntry::isDirectory)
        .let { grouped ->
            grouped[true].orEmpty().sortedWith(directedComparator) +
                grouped[false].orEmpty().sortedWith(directedComparator)
        }
}

private fun remoteFileType(name: String): String =
    name.substringAfterLast('.', missingDelimiterValue = "").lowercase()

internal fun formatRemoteModifiedTime(epochSeconds: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epochSeconds * 1_000L))

internal fun toggleRemoteFileSelection(selected: Set<String>, fileName: String): Set<String> =
    if (fileName in selected) selected - fileName else selected + fileName

internal fun childRemoteDirectory(parent: String, childName: String): String {
    require(childName.isNotBlank() && '/' !in childName && childName != "." && childName != "..") {
        "Invalid remote directory"
    }
    return listOf(parent.trim('/'), childName).filter { it.isNotEmpty() }.joinToString("/")
}

internal fun parentRemoteDirectory(directory: String): String = directory.substringBeforeLast('/', "")

internal fun remoteDirectoryLabel(directory: String): String =
    if (directory.isEmpty()) "PROJECT /" else "PROJECT / $directory"

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.1f GB".format(mb / 1024.0)
}
