package se.joynes.aiterminalhub.ui.screen.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import se.joynes.aiterminalhub.data.model.Project
import se.joynes.aiterminalhub.data.repository.ProjectRepository
import javax.inject.Inject

data class AddEditProjectState(
    val name: String = "",
    val useTmux: Boolean = true,
    val customScript: String = "cd {{PROJECT_PATH}}",
    val aiCommand: String = "",
    val saved: Boolean = false
)

@HiltViewModel
class AddEditProjectViewModel @Inject constructor(
    private val repo: ProjectRepository
) : ViewModel() {
    private val _state = MutableStateFlow(AddEditProjectState())
    val state: StateFlow<AddEditProjectState> = _state.asStateFlow()
    private var editingId: Long? = null
    private var serverId: Long = 0L

    fun loadProject(sId: Long, id: Long?) {
        serverId = sId
        if (id == null) return
        viewModelScope.launch {
            val p = repo.getById(id) ?: return@launch
            editingId = id
            _state.value = AddEditProjectState(
                name = p.name,
                useTmux = p.useTmux,
                customScript = p.customScript,
                aiCommand = p.aiCommand
            )
        }
    }

    fun update(block: AddEditProjectState.() -> AddEditProjectState) {
        _state.value = _state.value.block()
    }

    fun save() {
        viewModelScope.launch {
            val s = _state.value
            val project = Project(
                id = editingId ?: 0L,
                serverId = serverId,
                name = s.name,
                useTmux = s.useTmux,
                customScript = s.customScript,
                aiCommand = s.aiCommand
            )
            if (editingId != null) repo.update(project) else repo.save(project)
            _state.value = s.copy(saved = true)
        }
    }
}
