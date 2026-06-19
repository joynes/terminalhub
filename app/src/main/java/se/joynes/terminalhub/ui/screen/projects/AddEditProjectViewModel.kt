package se.joynes.terminalhub.ui.screen.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import se.joynes.terminalhub.BuildConfig
import se.joynes.terminalhub.data.model.LOCAL_PROJECT_SERVER_ID
import se.joynes.terminalhub.data.model.Project
import se.joynes.terminalhub.data.model.ProjectTargetType
import se.joynes.terminalhub.data.repository.ProjectRepository
import se.joynes.terminalhub.data.repository.ServerRepository
import javax.inject.Inject

data class ProjectServerOption(
    val id: Long,
    val name: String
)

data class AddEditProjectState(
    val targetType: ProjectTargetType = if (BuildConfig.IS_DIAGNOSTIC) ProjectTargetType.LOCAL else ProjectTargetType.SSH,
    val selectedServerId: Long? = null,
    val serverOptions: List<ProjectServerOption> = emptyList(),
    val name: String = "",
    val useTmux: Boolean = !BuildConfig.IS_DIAGNOSTIC,
    val customScript: String = "cd {{PROJECT_PATH}}",
    val aiCommand: String = "",
    val colorSeed: Int = 0,
    val gitUrl: String = "",
    val saved: Boolean = false
)

@HiltViewModel
class AddEditProjectViewModel @Inject constructor(
    private val repo: ProjectRepository,
    private val serverRepo: ServerRepository
) : ViewModel() {
    private val _state = MutableStateFlow(AddEditProjectState())
    val state: StateFlow<AddEditProjectState> = _state.asStateFlow()
    private var editingId: Long? = null

    fun loadProject(initialServerId: Long?, id: Long?) {
        viewModelScope.launch {
            val servers = serverRepo.getAll().first()
            val options = servers.map { ProjectServerOption(it.id, it.name) }
            val fallbackServerId = initialServerId ?: servers.firstOrNull()?.id
            _state.update { state ->
                state.copy(
                    serverOptions = options,
                    selectedServerId = if (state.targetType == ProjectTargetType.LOCAL) {
                        null
                    } else {
                        state.selectedServerId ?: fallbackServerId
                    }
                )
            }

            if (id == null) return@launch
            val p = repo.getById(id) ?: return@launch
            editingId = id
            _state.value = AddEditProjectState(
                targetType = p.targetType,
                selectedServerId = p.serverId,
                serverOptions = options,
                name = p.name,
                useTmux = p.useTmux,
                customScript = p.customScript,
                aiCommand = p.aiCommand,
                colorSeed = p.colorSeed,
                gitUrl = p.gitUrl
            )
        }
    }

    fun update(block: AddEditProjectState.() -> AddEditProjectState) {
        _state.value = _state.value.block()
    }

    fun save() {
        viewModelScope.launch {
            val s = _state.value
            val selectedServerId = if (s.targetType == ProjectTargetType.LOCAL) {
                LOCAL_PROJECT_SERVER_ID
            } else {
                s.selectedServerId ?: return@launch
            }
            val project = Project(
                id = editingId ?: 0L,
                serverId = selectedServerId,
                targetType = s.targetType,
                name = s.name,
                useTmux = s.useTmux,
                customScript = s.customScript,
                aiCommand = s.aiCommand,
                colorSeed = s.colorSeed,
                gitUrl = normalizeGitUrl(s.gitUrl, s.targetType)
            )
            if (editingId != null) repo.update(project) else repo.save(project)
            _state.value = s.copy(saved = true)
        }
    }

    companion object {
        fun normalizeGitUrl(gitUrl: String, targetType: ProjectTargetType): String {
            val trimmed = gitUrl.trim()
            if (targetType != ProjectTargetType.SSH) return trimmed
            val githubHttps = Regex("^https://github\\.com/([^/]+)/([^/]+?)(?:\\.git)?/?$")
            val match = githubHttps.matchEntire(trimmed) ?: return trimmed
            val owner = match.groupValues[1]
            val repo = match.groupValues[2]
            return "git@github.com:$owner/$repo.git"
        }
    }
}
