package se.joynes.terminalhub.ui.screen.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import se.joynes.terminalhub.data.model.Project
import se.joynes.terminalhub.data.model.Server
import se.joynes.terminalhub.data.repository.ProjectRepository
import se.joynes.terminalhub.data.repository.ServerRepository
import se.joynes.terminalhub.data.ssh.*

data class TmuxWindowsState(
    val projectId: Long? = null,
    val snapshot: TmuxWindowsSnapshot? = null,
    val connected: Boolean = false,
    val loading: Boolean = false,
    val busy: Boolean = false,
    val operation: String? = null,
    val error: String? = null,
    val selectionRevision: Long = 0
)

@HiltViewModel
class TmuxWindowsViewModel @Inject constructor(
    private val projects: ProjectRepository,
    private val servers: ServerRepository,
    private val remote: RemoteTmuxWindows,
    private val layouts: TmuxWindowLayoutStore
) : ViewModel() {
    private data class Binding(val project: Project, val server: Server)
    private val mutable = MutableStateFlow(TmuxWindowsState())
    val state = mutable.asStateFlow()
    private var binding: Binding? = null
    private var token: String? = null
    private var generation = 0L
    private var polling: Job? = null
    private var operation: Job? = null
    private val gate = Mutex()
    private val snapshots = mutableMapOf<Long, TmuxWindowsSnapshot>()

    fun bind(projectId: Long?, connectionToken: String?, connected: Boolean) {
        if (mutable.value.projectId == projectId && token == connectionToken && mutable.value.connected == connected) return
        pause()
        val previous = snapshots[projectId]
        token = connectionToken
        mutable.value = TmuxWindowsState(projectId, previous, connected, loading = connected,
            selectionRevision = mutable.value.selectionRevision)
        if (projectId == null || !connected || connectionToken == null) return
        val run = generation
        polling = viewModelScope.launch {
            try {
                val project = requireNotNull(projects.getById(projectId)) { "Project no longer exists." }
                require(!project.isLocal && project.useTmux) { "This project does not use tmux." }
                val server = requireNotNull(servers.getById(project.serverId)) { "Server no longer exists." }
                val current = Binding(project, server)
                binding = current
                while (isActive) {
                    if (operation?.isActive != true) refresh(current, run)
                    delay(3000)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { failure(run, error) }
        }
    }

    fun pause() {
        generation++
        polling?.cancel()
        operation?.cancel()
        binding = null
        token = null
        mutable.value = mutable.value.copy(connected = false, loading = false, busy = false, operation = null)
    }

    private suspend fun refresh(current: Binding, run: Long) {
        try { gate.withLock { publish(run, remote.list(current.server, current.project)) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { failure(run, error) }
    }

    fun refresh() {
        val current = binding ?: return
        if (mutable.value.busy) return
        val run = generation
        mutable.value = mutable.value.copy(loading = true, error = null)
        viewModelScope.launch { refresh(current, run) }
    }

    fun create(name: String) = mutate("Opening terminal…") { current, snapshot -> remote.create(current.server, current.project, snapshot, name) }
    fun select(window: TmuxWindow) = mutate("Switching terminal…") { current, snapshot -> remote.select(current.server, snapshot, window) }
    fun rename(window: TmuxWindow, name: String) = mutate("Renaming terminal…") { current, snapshot -> remote.rename(current.server, snapshot, window, name) }
    fun close(window: TmuxWindow) = mutate("Closing terminal…") { current, snapshot -> remote.close(current.server, snapshot, window) }

    private fun mutate(label: String, action: suspend (Binding, TmuxWindowsSnapshot) -> TmuxWindowsSnapshot) {
        val current = binding ?: return
        if (mutable.value.busy || !mutable.value.connected || mutable.value.snapshot == null) return
        val run = generation
        mutable.value = mutable.value.copy(busy = true, operation = label, error = null)
        operation = viewModelScope.launch {
            try {
                gate.withLock {
                    if (run != generation) return@withLock
                    val snapshot = requireNotNull(mutable.value.snapshot)
                    publish(run, action(current, snapshot))
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { failure(run, error) }
            finally { if (run == generation) mutable.value = mutable.value.copy(busy = false, operation = null) }
        }
    }

    private suspend fun publish(run: Long, snapshot: TmuxWindowsSnapshot) {
        if (run != generation) return
        val current = binding ?: return
        layouts.save(current.server, current.project, snapshot)
        val memories = layouts.read(current.server, current.project)?.memories.orEmpty().associateBy { it.key }
        val enriched = snapshot.copy(windows = snapshot.windows.map { it.copy(lastProgram = memories[it.key]?.program) })
        if (run != generation) return
        val previousActive = mutable.value.snapshot?.windows?.firstOrNull { it.active }?.id
        val changed = previousActive != null && previousActive != snapshot.windows.firstOrNull { it.active }?.id
        mutable.value.projectId?.let { snapshots[it] = enriched }
        mutable.value = mutable.value.copy(snapshot = enriched, loading = false, error = null,
            selectionRevision = mutable.value.selectionRevision + if (changed) 1 else 0)
    }

    private fun failure(run: Long, error: Exception) {
        if (run == generation) mutable.value = mutable.value.copy(loading = false,
            error = error.message ?: "Could not read project terminals. Refresh to try again.")
    }
}
