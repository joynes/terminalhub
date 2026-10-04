package se.joynes.terminalhub.data.notes

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import se.joynes.terminalhub.data.db.dao.ProjectNoteDao
import se.joynes.terminalhub.data.db.entity.ProjectNoteEntity
import se.joynes.terminalhub.data.repository.ProjectRepository
import se.joynes.terminalhub.data.repository.ServerRepository

enum class NoteSyncStatus { LOCAL_ONLY, SYNCING, SYNCED, OFFLINE_PENDING, CONFLICT, ERROR }
enum class NoteSyncDecision { EMPTY, PULL, PUSH, CONFLICT, UNCHANGED }
fun noteSyncDecision(local: ProjectNoteEntity?, remote: RemoteNote): NoteSyncDecision {
    if (local == null) return if (remote.text == null) NoteSyncDecision.EMPTY else NoteSyncDecision.PULL
    if (remote.text == null && local.lastSyncedContentHash == null && local.text.isNotEmpty()) return NoteSyncDecision.PUSH
    if (!local.dirty) return if (local.lastSyncedContentHash != remote.hash) NoteSyncDecision.PULL else NoteSyncDecision.UNCHANGED
    if (noteHash(local.text) == remote.hash && !local.pendingDelete) return NoteSyncDecision.PULL
    if (local.lastSyncedContentHash != remote.hash) return NoteSyncDecision.CONFLICT
    return if (local.text.isEmpty() && !local.pendingDelete && remote.text == null) NoteSyncDecision.EMPTY else NoteSyncDecision.PUSH
}

data class ProjectNoteState(
    val note: ProjectNoteEntity? = null,
    val loaded: Boolean = false,
    val status: NoteSyncStatus = NoteSyncStatus.LOCAL_ONLY,
    val conflict: RemoteNote? = null
)

/** Immediate Room working copy; only remote writes are debounced. No terminal/history access. */
@Singleton
class ProjectNotesRepository @Inject constructor(
    private val dao: ProjectNoteDao,
    private val projects: ProjectRepository,
    private val servers: ServerRepository,
    private val remote: RemoteProjectNotesStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val states = mutableMapOf<Long, MutableStateFlow<ProjectNoteState>>()
    private val localMutex = Mutex()
    private val syncLocks = mutableMapOf<Long, Mutex>()
    private val pendingSync = mutableMapOf<Long, Job>()
    private val runningSync = mutableSetOf<Job>()
    private val pendingSaves = mutableSetOf<Job>()

    fun observe(id: Long): StateFlow<ProjectNoteState> = states.getOrPut(id) { MutableStateFlow(ProjectNoteState()) }.asStateFlow()
    private fun state(id: Long) = states.getOrPut(id) { MutableStateFlow(ProjectNoteState()) }

    suspend fun load(id: Long) {
        localMutex.withLock {
            if (!state(id).value.loaded) state(id).value = ProjectNoteState(dao.get(id), loaded = true)
        }
        retry(id)
    }

    fun edit(id: Long, text: String, clear: Boolean = false) {
        if (!state(id).value.loaded) return
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_NOTE_BYTES) { "Note exceeds 256 KB" }
        val previous = state(id).value
        val note = (previous.note ?: ProjectNoteEntity(id)).copy(
            text = text, dirty = true, pendingDelete = clear,
            localUpdatedAt = maxOf(System.currentTimeMillis(), (previous.note?.localUpdatedAt ?: 0) + 1),
            syncError = null
        )
        state(id).value = previous.copy(note = note, status = if (previous.conflict == null) NoteSyncStatus.OFFLINE_PENDING else NoteSyncStatus.CONFLICT)
        val job = scope.launch {
            localMutex.withLock {
                // Coalesce queued writes; persistence can never finish out of order.
                state(id).value.note?.let { dao.save(it) }
            }
        }
        pendingSaves.add(job)
        job.invokeOnCompletion { pendingSaves.remove(job) }
        pendingSync.remove(id)?.cancel()
        pendingSync[id] = scope.launch { delay(1500); startSync(id) }
    }

    suspend fun flush() {
        pendingSaves.toList().joinAll()
    }

    fun retry(id: Long) {
        pendingSync.remove(id)?.cancel()
        pendingSync[id] = scope.launch { flush(); startSync(id) }
    }

    private fun startSync(id: Long, resolution: Boolean? = null) {
        val job = scope.launch { flush(); sync(id, resolution) }
        runningSync.add(job)
        job.invokeOnCompletion { runningSync.remove(job) }
    }

    suspend fun resetForImport() {
        pendingSync.values.forEach { it.cancel() }
        pendingSync.values.toList().joinAll()
        pendingSync.clear()
        runningSync.toList().forEach { it.cancel() }
        runningSync.toList().joinAll()
        flush()
        states.clear()
    }

    private suspend fun sync(id: Long, resolution: Boolean? = null) {
        syncLocks.getOrPut(id) { Mutex() }.withLock {
            val project = projects.getById(id) ?: return
            val snapshot = state(id).value.note
            if (project.isLocal) {
                state(id).value = state(id).value.copy(status = NoteSyncStatus.LOCAL_ONLY)
                return
            }
            val server = servers.getById(project.serverId) ?: return
            if (state(id).value.conflict != null && resolution == null) return
            val conflict = state(id).value.conflict
            state(id).value = state(id).value.copy(status = NoteSyncStatus.SYNCING)
            try {
                var fetched = remote.read(server, project)
                if (resolution != null && conflict != null && conflict.hash != fetched.hash) throw NoteConflict(fetched)
                val decision = when (resolution) {
                    true -> NoteSyncDecision.PUSH
                    false -> NoteSyncDecision.PULL
                    null -> noteSyncDecision(snapshot, fetched)
                }
                if (decision == NoteSyncDecision.CONFLICT) throw NoteConflict(fetched)
                if (decision == NoteSyncDecision.PUSH && snapshot != null) {
                    fetched = remote.write(server, project, snapshot.text, fetched, snapshot.pendingDelete)
                }
                localMutex.withLock {
                    val latest = state(id).value.note
                    val editedDuringSync = latest?.localUpdatedAt != snapshot?.localUpdatedAt
                    if (editedDuringSync && decision == NoteSyncDecision.PULL &&
                        latest?.lastSyncedContentHash != fetched.hash) throw NoteConflict(fetched)
                    val synced = if (editedDuringSync) {
                        (latest ?: ProjectNoteEntity(id)).copy(
                            remoteKey = fetched.key, lastSyncedRemoteMtime = fetched.mtime,
                            lastSyncedContentHash = fetched.hash, dirty = true, syncError = null
                        )
                    } else {
                        ProjectNoteEntity(id, fetched.text.orEmpty(), snapshot?.localUpdatedAt ?: 0,
                            remoteKey = fetched.key, lastSyncedRemoteMtime = fetched.mtime,
                            lastSyncedContentHash = fetched.hash)
                    }
                    dao.save(synced)
                    state(id).value = ProjectNoteState(synced, true,
                        if (editedDuringSync) NoteSyncStatus.OFFLINE_PENDING else NoteSyncStatus.SYNCED)
                    // A subsequent edit already schedules a debounced sync; avoid self-cancellation.
                }
            } catch (error: CancellationException) {
                throw error
            } catch (conflictError: NoteConflict) {
                state(id).value = state(id).value.copy(status = NoteSyncStatus.CONFLICT, conflict = conflictError.remote)
            } catch (error: Exception) {
                localMutex.withLock {
                    val latest = state(id).value.note
                    val message = when {
                        error is com.trilead.ssh2.AtomicSftpRename.UnsupportedServerException ->
                            "Server needs the OpenSSH atomic-rename SFTP extension to update notes safely. Your local and server copies are unchanged."
                        error is IllegalArgumentException && error.message == "Note exceeds 256 KB" ->
                            "Server note exceeds 256 KB. Reduce its size on the server before syncing."
                        else -> "Could not sync. Check connection and retry."
                    }
                    val failed = (latest ?: ProjectNoteEntity(id)).copy(syncError = message)
                    dao.save(failed)
                    state(id).value = state(id).value.copy(note = failed,
                        status = if (failed.dirty && error !is com.trilead.ssh2.AtomicSftpRename.UnsupportedServerException &&
                            error !is IllegalArgumentException) NoteSyncStatus.OFFLINE_PENDING else NoteSyncStatus.ERROR)
                }
            }
        }
    }

    fun resolve(id: Long, keepDevice: Boolean) {
        startSync(id, keepDevice)
    }
}
