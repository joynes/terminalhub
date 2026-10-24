package se.joynes.terminalhub.ui.screen.git

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import se.joynes.terminalhub.data.git.*
import se.joynes.terminalhub.data.repository.ProjectRepository
import se.joynes.terminalhub.data.repository.ServerRepository
import se.joynes.terminalhub.domain.ScriptTemplateEngine

data class GitReviewState(
    val projectName: String = "",
    val repositories: List<String> = emptyList(),
    val repository: String? = null,
    val mode: GitReviewMode = GitReviewMode.ALL,
    val snapshot: GitReviewSnapshot? = null,
    val file: GitChangedFile? = null,
    val patch: String? = null,
    val truncated: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class GitReviewViewModel @Inject constructor(
    private val projects: ProjectRepository,
    private val servers: ServerRepository,
    private val templates: ScriptTemplateEngine,
    private val executor: RemoteGitExecutor
) : ViewModel() {
    private val mutable = MutableStateFlow(GitReviewState())
    val state = mutable.asStateFlow()
    private var projectId: Long? = null
    private var reader: GitReviewReader? = null
    private var work: Job? = null

    fun open(id: Long) {
        work?.cancel()
        projectId = id
        reader = null
        mutable.value = GitReviewState(loading = true)
        work = viewModelScope.launch {
            attempt {
                val project = requireNotNull(projects.getById(id)) { "Project no longer exists." }
                require(!project.isLocal) { "Git review currently requires an SSH project." }
                val server = requireNotNull(servers.getById(project.serverId)) { "Server no longer exists." }
                val source = GitReviewReader { executor.run(server, it) }
                reader = source
                val repos = source.repositories(templates.projectPath(server, project))
                mutable.value = GitReviewState(projectName = project.name, repositories = repos,
                    repository = repos.singleOrNull(), loading = repos.size == 1)
                repos.singleOrNull()?.let { loadSnapshot(source, it, GitReviewMode.ALL) }
            }
        }
    }

    fun selectRepository(repo: String) {
        if (repo !in mutable.value.repositories) return
        load(repo, mutable.value.mode)
    }

    fun selectMode(mode: GitReviewMode) {
        mutable.value.repository?.let { load(it, mode) }
    }

    fun refresh() {
        val current = mutable.value
        if (reader == null || current.repository == null) projectId?.let(::open)
        else load(current.repository, current.mode)
    }

    private fun load(repo: String, mode: GitReviewMode) {
        work?.cancel()
        mutable.value = mutable.value.copy(repository = repo, mode = mode, snapshot = null, file = null,
            patch = null, truncated = false, loading = true, error = null)
        work = viewModelScope.launch { attempt { reader?.let { loadSnapshot(it, repo, mode) } } }
    }

    private suspend fun loadSnapshot(source: GitReviewReader, repo: String, mode: GitReviewMode) {
        val snapshot = source.snapshot(repo, mode)
        mutable.value = mutable.value.copy(snapshot = snapshot, loading = false)
    }

    fun selectFile(file: GitChangedFile) {
        val current = mutable.value
        val repo = current.repository ?: return
        if (file !in current.snapshot?.files.orEmpty()) return
        work?.cancel()
        mutable.value = current.copy(file = file, patch = null, truncated = false, loading = true, error = null)
        work = viewModelScope.launch {
            attempt {
                val diff = requireNotNull(reader).diff(repo, current.mode, file)
                mutable.value = mutable.value.copy(patch = diff.output, truncated = diff.truncated, loading = false)
            }
        }
    }

    fun backToFiles() {
        work?.cancel()
        mutable.value = mutable.value.copy(file = null, patch = null, loading = false, error = null)
    }

    fun close() { work?.cancel() }

    private suspend fun attempt(block: suspend () -> Unit) {
        try { block() } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            mutable.value = mutable.value.copy(loading = false, error = error.message ?: "Could not load Git changes. Try again.")
        }
    }
}
