package se.joynes.aiterminal.data.db.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import se.joynes.aiterminal.data.db.entity.ProjectEntity

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY name ASC")
    fun getAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE serverId = :serverId ORDER BY name ASC")
    fun getByServer(serverId: Long): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getById(id: Long): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ProjectEntity): Long

    @Update
    suspend fun update(entity: ProjectEntity)

    @Delete
    suspend fun delete(entity: ProjectEntity)

    @Query("DELETE FROM projects")
    suspend fun clearAll()

    @Query("UPDATE projects SET lastOpenedAt = :timestamp WHERE id = :id")
    suspend fun updateLastOpenedAt(id: Long, timestamp: Long)
}
