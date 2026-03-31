package se.joynes.aiterminalhub.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import se.joynes.aiterminalhub.data.db.dao.ProjectDao
import se.joynes.aiterminalhub.data.db.entity.ProjectEntity
import se.joynes.aiterminalhub.data.model.Project
import se.joynes.aiterminalhub.data.model.ProjectTargetType
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

    suspend fun save(project: Project): Long = dao.insert(project.withColorSeed().toEntity())

    suspend fun update(project: Project) = dao.update(project.withColorSeed().toEntity())

    suspend fun delete(project: Project) = dao.delete(project.toEntity())

    private fun ProjectEntity.toModel() = Project(
        id = id,
        serverId = serverId,
        targetType = ProjectTargetType.valueOf(targetType.uppercase()),
        name = name,
        useTmux = useTmux,
        customScript = customScript,
        aiCommand = aiCommand,
        colorSeed = colorSeed,
        gitUrl = gitUrl
    )

    private fun Project.toEntity() = ProjectEntity(
        id = id,
        serverId = serverId,
        targetType = targetType.name.lowercase(),
        name = name,
        useTmux = useTmux,
        customScript = customScript,
        aiCommand = aiCommand,
        colorSeed = colorSeed,
        gitUrl = gitUrl
    )

    private fun Project.withColorSeed(): Project {
        if (colorSeed != 0) return this
        val fallbackSeed = ((name.hashCode().toLong() shl 32) xor System.nanoTime()).toInt()
        return copy(colorSeed = if (fallbackSeed != 0) fallbackSeed else 1)
    }
}
