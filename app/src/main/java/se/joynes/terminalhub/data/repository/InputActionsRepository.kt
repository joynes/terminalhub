package se.joynes.terminalhub.data.repository

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import se.joynes.terminalhub.data.db.dao.PinnedActionDao
import se.joynes.terminalhub.data.db.dao.TextInputHistoryDao
import se.joynes.terminalhub.data.db.entity.PinnedActionEntity
import se.joynes.terminalhub.data.db.entity.TextInputHistoryEntity
import se.joynes.terminalhub.domain.TerminalInputHistoryRecorder

@Singleton
class InputActionsRepository @Inject constructor(
    private val history: TextInputHistoryDao,
    private val pins: PinnedActionDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pending = Channel<Pair<Long, String>>(Channel.UNLIMITED)
    val recorder = TerminalInputHistoryRecorder { project, text -> pending.trySend(project to text) }

    init {
        scope.launch {
            for ((project, text) in pending) {
                try {
                    history.saveRecent(TextInputHistoryEntity(projectId = project, text = text))
                } catch (_: Exception) {
                    // Never log input, exception messages may include SQL bind arguments.
                    Log.w("InputHistory", "Unable to save local input history")
                }
            }
        }
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
