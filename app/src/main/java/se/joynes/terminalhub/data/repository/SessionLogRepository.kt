package se.joynes.terminalhub.data.repository

import kotlinx.coroutines.flow.Flow
import se.joynes.terminalhub.data.db.dao.SessionLogDao
import se.joynes.terminalhub.data.db.entity.SessionLogEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionLogRepository @Inject constructor(
    private val dao: SessionLogDao
) {
    fun getAll(): Flow<List<SessionLogEntity>> = dao.getAll()
    fun getByServer(serverId: Long): Flow<List<SessionLogEntity>> = dao.getByServer(serverId)
    suspend fun getById(id: Long): SessionLogEntity? = dao.getById(id)
    suspend fun insert(entity: SessionLogEntity): Long = dao.insert(entity)
    suspend fun update(entity: SessionLogEntity) = dao.update(entity)
}
