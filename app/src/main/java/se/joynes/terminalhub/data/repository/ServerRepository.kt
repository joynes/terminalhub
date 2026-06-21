package se.joynes.terminalhub.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import se.joynes.terminalhub.data.db.dao.ProjectDao
import se.joynes.terminalhub.data.db.dao.ServerDao
import se.joynes.terminalhub.data.db.entity.ServerEntity
import se.joynes.terminalhub.data.model.Server
import se.joynes.terminalhub.data.security.SecurePrefsManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerRepository @Inject constructor(
    private val dao: ServerDao,
    private val projectDao: ProjectDao,
    private val securePrefsManager: SecurePrefsManager
) {
    fun getAll(): Flow<List<Server>> = dao.getAll().map { list ->
        list.map { it.toModel() }
    }

    suspend fun getById(id: Long): Server? = dao.getById(id)?.toModel()

    suspend fun save(server: Server): Long = dao.insert(server.toEntity())

    suspend fun update(server: Server) = dao.update(server.toEntity())

    suspend fun delete(server: Server) {
        projectDao.deleteByServer(server.id)
        securePrefsManager.deletePassword(server.id)
        securePrefsManager.deletePrivateKey(server.id)
        dao.delete(server.toEntity())
    }

    suspend fun clearAll() = dao.clearAll()

    private fun ServerEntity.toModel() = Server(id, name, host, port, username, authType, keyAlias, projectsFolder, setupScript)
    private fun Server.toEntity() = ServerEntity(id, name, host, port, username, authType, keyAlias, projectsFolder, setupScript)
}
