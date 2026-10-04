package se.joynes.terminalhub.data.repository

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong
import se.joynes.terminalhub.data.settings.AppSettingsRepository
import se.joynes.terminalhub.data.db.dao.PinnedActionDao
import se.joynes.terminalhub.data.db.dao.TextInputHistoryDao
import se.joynes.terminalhub.data.db.entity.PinnedActionEntity
import se.joynes.terminalhub.data.db.entity.TextInputHistoryEntity
import se.joynes.terminalhub.domain.TerminalInputHistoryRecorder

@Singleton
class InputActionsRepository @Inject constructor(
    private val history: TextInputHistoryDao,
    private val pins: PinnedActionDao,
    private val settingsRepository: AppSettingsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private data class PendingInput(val project: Long, val text: String, val generation: Long)
    private val pending = Channel<PendingInput>(Channel.UNLIMITED)
    private val generation = AtomicLong()
    private val historyWrites = Mutex()
    @Volatile private var inFlight: Job? = null
    val recorder = TerminalInputHistoryRecorder(
        enabled = { settingsRepository.settings.value.inputHistoryEnabled }
    ) { project, text ->
        if (settingsRepository.settings.value.inputHistoryEnabled) {
            pending.trySend(PendingInput(project, text, generation.get()))
        }
    }

    init {
        settingsRepository.onInputHistoryDisabled(::discardPendingInput)
        scope.launch {
            for (input in pending) {
                val write = scope.launch(start = CoroutineStart.LAZY) {
                    historyWrites.withLock {
                        if (!settingsRepository.settings.value.inputHistoryEnabled || input.generation != generation.get()) return@withLock
                        try {
                            history.saveRecent(TextInputHistoryEntity(projectId = input.project, text = input.text))
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            // Never log input or exception messages containing SQL bind arguments.
                            Log.w("InputHistory", "Unable to save local input history")
                        }
                    }
                }
                inFlight = write
                write.start()
                write.join()
                inFlight = null
            }
        }
    }

    private fun discardPendingInput() = synchronized(recorder) {
        generation.incrementAndGet()
        recorder.clearAllDrafts()
        inFlight?.cancel()
        while (pending.tryReceive().isSuccess) { /* Discard queued input, including its text. */ }
    }

    fun setHistoryEnabled(enabled: Boolean) = settingsRepository.setInputHistoryEnabled(enabled)

    suspend fun clearHistory() {
        discardPendingInput()
        historyWrites.withLock { history.clearAll() }
    }

    fun history(projectId: Long) = history.getRecentForProject(projectId)
    fun pins(projectId: Long) = pins.forProject(projectId)
    suspend fun deleteHistory(id: Long) = history.delete(id)
    suspend fun deletePin(id: Long) = pins.delete(id)
    suspend fun markUsed(id: Long) = pins.markUsed(id, System.currentTimeMillis())
    suspend fun savePin(action: PinnedActionEntity) {
        require(action.text.isNotBlank())
        require(action.scope == "GLOBAL" || (action.scope == "PROJECT" && action.projectId != null))
        pins.save(action.copy(
            name = se.joynes.terminalhub.data.model.pinnedActionName(action.name, action.text),
            projectId = if (action.scope == "GLOBAL") null else action.projectId,
            updatedAt = System.currentTimeMillis()
        ))
    }
}
