package se.joynes.aiterminalhub.ui.screen.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import se.joynes.aiterminalhub.data.model.Project
import se.joynes.aiterminalhub.data.repository.ProjectRepository
import javax.inject.Inject

@HiltViewModel
class ProjectListViewModel @Inject constructor(
    private val repo: ProjectRepository
) : ViewModel() {
    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    fun loadProjects(serverId: Long) {
        repo.getByServer(serverId)
            .onEach { _projects.value = it }
            .launchIn(viewModelScope)
    }
}
