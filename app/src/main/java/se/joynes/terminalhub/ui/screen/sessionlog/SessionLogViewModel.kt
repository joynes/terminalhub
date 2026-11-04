package se.joynes.terminalhub.ui.screen.sessionlog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import se.joynes.terminalhub.data.db.entity.SessionLogEntity
import se.joynes.terminalhub.data.repository.SessionLogRepository
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Context
import android.net.Uri

@HiltViewModel
class SessionLogViewModel @Inject constructor(
    private val repo: SessionLogRepository
) : ViewModel() {
    val logs: StateFlow<List<SessionLogEntity>> = repo.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val exporting = MutableStateFlow(false)
    val exportMessage = MutableStateFlow<String?>(null)

    fun export(context: Context, destination: Uri) {
        if (exporting.value) return
        exporting.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val snapshot = repo.getAll().first()
                    (context.contentResolver.openOutputStream(destination, "wt") ?: error("Cannot open export destination"))
                        .bufferedWriter().use { writer ->
                            writer.appendLine("TerminalHub session log")
                            snapshot.forEach { entry ->
                                writer.appendLine("Session: ${entry.sessionId}")
                                writer.appendLine("Server: ${entry.serverId}; project: ${entry.projectId ?: "none"}")
                                writer.appendLine("Started: ${entry.startedAt}; ended: ${entry.endedAt ?: "running"}")
                                entry.outputPath?.let { writer.appendLine("Output: $it") }
                                writer.appendLine()
                            }
                        }
                }
                exportMessage.value = "Session log exported"
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) { exportMessage.value = error.message ?: "Session log export failed" }
            finally { exporting.value = false }
        }
    }
}
