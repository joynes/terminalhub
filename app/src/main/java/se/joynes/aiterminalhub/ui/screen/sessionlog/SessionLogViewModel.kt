package se.joynes.aiterminalhub.ui.screen.sessionlog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import se.joynes.aiterminalhub.data.db.entity.SessionLogEntity
import se.joynes.aiterminalhub.data.repository.SessionLogRepository
import javax.inject.Inject

@HiltViewModel
class SessionLogViewModel @Inject constructor(
    private val repo: SessionLogRepository
) : ViewModel() {
    val logs: StateFlow<List<SessionLogEntity>> = repo.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun export() {
        // TODO: implement log export
    }
}
