package se.joynes.aiterminal.ui.screen.servers

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import se.joynes.aiterminal.data.model.Server
import se.joynes.aiterminal.data.repository.ServerRepository
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
