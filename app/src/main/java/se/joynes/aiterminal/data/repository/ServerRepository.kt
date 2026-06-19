package se.joynes.aiterminal.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import se.joynes.aiterminal.data.db.dao.ServerDao
import se.joynes.aiterminal.data.db.entity.ServerEntity
import se.joynes.aiterminal.data.model.Server
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerRepository @Inject constructor(
    private val dao: ServerDao
) {
    fun getAll(): Flow<List<Server>> = dao.getAll().map { list ->
        list.map { it.toModel() }
    }

    suspend fun getById(id: Long): Server? = dao.getById(id)?.toModel()

    suspend fun save(server: Server): Long = dao.insert(server.toEntity())

    suspend fun update(server: Server) = dao.update(server.toEntity())

    suspend fun delete(server: Server) = dao.delete(server.toEntity())

    suspend fun clearAll() = dao.clearAll()

    private fun ServerEntity.toModel() = Server(id, name, host, port, username, authType, keyAlias, projectsFolder, setupScript)
    private fun Server.toEntity() = ServerEntity(id, name, host, port, username, authType, keyAlias, projectsFolder, setupScript)
}
