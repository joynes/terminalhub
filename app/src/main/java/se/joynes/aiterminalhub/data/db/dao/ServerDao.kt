package se.joynes.aiterminalhub.data.db.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import se.joynes.aiterminalhub.data.db.entity.ServerEntity

@Dao
interface ServerDao {
    @Query("SELECT * FROM servers ORDER BY name ASC")
    fun getAll(): Flow<List<ServerEntity>>

    @Query("SELECT * FROM servers WHERE id = :id")
    suspend fun getById(id: Long): ServerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ServerEntity): Long

    @Update
    suspend fun update(entity: ServerEntity)

    @Delete
    suspend fun delete(entity: ServerEntity)
}
