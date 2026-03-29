package se.joynes.aiterminalhub.ui.screen.export

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import se.joynes.aiterminalhub.data.export.ExportImportManager
import se.joynes.aiterminalhub.data.export.ImportResult
import javax.inject.Inject

sealed interface ExportImportState {
    object Idle : ExportImportState
    object Working : ExportImportState
    data class ImportDone(val result: ImportResult) : ExportImportState
    object ExportDone : ExportImportState
    data class Error(val message: String) : ExportImportState
}

@HiltViewModel
class ExportImportViewModel @Inject constructor(
    private val manager: ExportImportManager
) : ViewModel() {
    private val _state = MutableStateFlow<ExportImportState>(ExportImportState.Idle)
    val state: StateFlow<ExportImportState> = _state.asStateFlow()

    fun export(context: Context, uri: Uri) {
        _state.value = ExportImportState.Working
        viewModelScope.launch {
            try {
                manager.exportYaml(context, uri)
                _state.value = ExportImportState.ExportDone
            } catch (e: Exception) {
                _state.value = ExportImportState.Error(e.message ?: "Export failed")
            }
        }
    }

    fun import(context: Context, uri: Uri) {
        _state.value = ExportImportState.Working
        viewModelScope.launch {
            try {
                val result = manager.importYaml(context, uri)
                _state.value = ExportImportState.ImportDone(result)
            } catch (e: Exception) {
                _state.value = ExportImportState.Error(e.message ?: "Import failed")
            }
        }
    }

    fun resetState() {
        _state.value = ExportImportState.Idle
    }
}
