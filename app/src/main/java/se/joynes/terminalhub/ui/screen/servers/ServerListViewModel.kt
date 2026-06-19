package se.joynes.terminalhub.ui.screen.servers

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import se.joynes.terminalhub.data.model.Server
import se.joynes.terminalhub.data.repository.ServerRepository
import javax.inject.Inject
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted

@HiltViewModel
class ServerListViewModel @Inject constructor(
    private val repo: ServerRepository
) : ViewModel() {
    val servers: StateFlow<List<Server>> = repo.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
