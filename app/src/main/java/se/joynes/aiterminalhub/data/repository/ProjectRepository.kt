package se.joynes.aiterminalhub.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import se.joynes.aiterminalhub.data.db.dao.ProjectDao
import se.joynes.aiterminalhub.data.db.entity.ProjectEntity
import se.joynes.aiterminalhub.data.model.Project
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectRepository @Inject constructor(
    private val dao: ProjectDao
) {
    fun getAll(): Flow<List<Project>> = dao.getAll().map { list -> list.map { it.toModel() } }

    fun getByServer(serverId: Long): Flow<List<Project>> =
        dao.getByServer(serverId).map { list -> list.map { it.toModel() } }

    suspend fun getById(id: Long): Project? = dao.getById(id)?.toModel()

    suspend fun save(project: Project): Long = dao.insert(project.toEntity())

    suspend fun update(project: Project) = dao.update(project.toEntity())

    suspend fun delete(project: Project) = dao.delete(project.toEntity())

    private fun ProjectEntity.toModel() = Project(id, serverId, name, setupScript, colorSeed)
    private fun Project.toEntity() = ProjectEntity(id, serverId, name, setupScript, colorSeed)
}
