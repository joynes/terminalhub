package se.joynes.aiterminal.ui.screen.upload

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import se.joynes.aiterminal.data.repository.ProjectRepository
import se.joynes.aiterminal.data.repository.ServerRepository
import se.joynes.aiterminal.data.security.SecurePrefsManager
import se.joynes.aiterminal.data.ssh.ScpUploader
import se.joynes.aiterminal.domain.ScriptTemplateEngine
import javax.inject.Inject

sealed interface UploadState {
    object Idle : UploadState
    data class Uploading(val fileName: String, val progress: Float) : UploadState
    data class Done(val fileName: String, val remotePath: String) : UploadState
    data class Error(val message: String) : UploadState
}

@HiltViewModel
class FileUploadViewModel @Inject constructor(
    private val serverRepo: ServerRepository,
    private val projectRepo: ProjectRepository,
    private val engine: ScriptTemplateEngine,
    private val scpUploader: ScpUploader,
    private val securePrefs: SecurePrefsManager
) : ViewModel() {

    private val _uploadStates = MutableStateFlow<Map<Long, UploadState>>(emptyMap())

    fun uploadState(projectId: Long): Flow<UploadState> =
        _uploadStates.map { it[projectId] ?: UploadState.Idle }

    fun reset(projectId: Long) {
        _uploadStates.update { states -> states - projectId }
    }

    fun startUpload(serverId: Long, projectId: Long, uri: Uri, context: Context) {
        if (_uploadStates.value[projectId] is UploadState.Uploading) return
        viewModelScope.launch {
            try {
                val server  = serverRepo.getById(serverId)  ?: error("Server not found")
                val project = projectRepo.getById(projectId) ?: error("Project not found")
                val remotePath = engine.projectPath(server, project)
                val (fileName, fileSize) = resolveFileInfo(context, uri)
                val stream = context.contentResolver.openInputStream(uri) ?: error("Cannot open file")

                setUploadState(projectId, UploadState.Uploading(fileName, 0f))

                scpUploader.upload(
                    server       = server,
                    password     = securePrefs.getPassword(server.id),
                    privateKeyPem = securePrefs.getPrivateKey(server.id),
                    fileName     = fileName,
                    fileSize     = fileSize,
                    inputStream  = stream,
                    remoteDir    = remotePath
                ).collect { progress ->
                    setUploadState(projectId, UploadState.Uploading(progress.fileName, progress.percent / 100f))
                }

                setUploadState(projectId, UploadState.Done(fileName, "$remotePath/$fileName"))
            } catch (e: Exception) {
                setUploadState(projectId, UploadState.Error(e.message ?: "Upload failed"))
            }
        }
    }

    private fun setUploadState(projectId: Long, state: UploadState) {
        _uploadStates.update { states -> states + (projectId to state) }
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
