package se.joynes.aiterminalhub.ui.screen.upload

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import se.joynes.aiterminalhub.data.repository.ProjectRepository
import se.joynes.aiterminalhub.data.repository.ServerRepository
import se.joynes.aiterminalhub.domain.ScriptTemplateEngine
import se.joynes.aiterminalhub.domain.TerminalSessionManager
import javax.inject.Inject

sealed interface UploadState {
    object Idle : UploadState
    data class Uploading(val fileName: String, val progress: Float) : UploadState
    data class Done(val fileName: String) : UploadState
    data class Error(val message: String) : UploadState
}

@HiltViewModel
class FileUploadViewModel @Inject constructor(
    private val serverRepo: ServerRepository,
    private val projectRepo: ProjectRepository,
    private val engine: ScriptTemplateEngine,
    private val sessionManager: TerminalSessionManager
) : ViewModel() {

    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState: StateFlow<UploadState> = _uploadState.asStateFlow()

    fun reset() { _uploadState.value = UploadState.Idle }

    fun startUpload(serverId: Long, projectId: Long, uri: Uri, context: Context) {
        if (_uploadState.value is UploadState.Uploading) return
        viewModelScope.launch {
            try {
                val server = serverRepo.getById(serverId) ?: error("Server not found")
                val project = projectRepo.getById(projectId) ?: error("Project not found")
                val remotePath = engine.projectPath(server, project)
                val conn = sessionManager.getConnectionForProject(projectId)
                    ?: error("No active connection for project")

                val (fileName, fileSize) = resolveFileInfo(context, uri)
                val stream = context.contentResolver.openInputStream(uri)
                    ?: error("Cannot open file")

                _uploadState.value = UploadState.Uploading(fileName, 0f)

                conn.scpUpload(fileName, fileSize, stream, remotePath).collect { progress ->
                    _uploadState.value = UploadState.Uploading(
                        fileName = progress.fileName,
                        progress = progress.percent / 100f
                    )
                }

                _uploadState.value = UploadState.Done(fileName)
            } catch (e: Exception) {
                _uploadState.value = UploadState.Error(e.message ?: "Upload failed")
            }
        }
    }

    private fun resolveFileInfo(context: Context, uri: Uri): Pair<String, Long> {
        var name = "file"
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
                if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
            }
        }
        return name to size
    }
}
