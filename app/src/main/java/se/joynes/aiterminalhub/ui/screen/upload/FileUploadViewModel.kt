package se.joynes.aiterminalhub.ui.screen.upload

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class UploadItem(val name: String, val uri: Uri, val progress: Float = 0f)

@HiltViewModel
class FileUploadViewModel @Inject constructor() : ViewModel() {
    private val _uploads = MutableStateFlow<List<UploadItem>>(emptyList())
    val uploads: StateFlow<List<UploadItem>> = _uploads.asStateFlow()

    fun addFiles(uris: List<Uri>, context: Context) {
        val items = uris.map { uri ->
            val name = uri.lastPathSegment ?: uri.toString()
            UploadItem(name = name, uri = uri)
        }
        _uploads.value = items
    }

    fun startUpload(serverId: Long) {
        // TODO: Wire to SftpUploader once SshManager exposes JSch session
    }
}
