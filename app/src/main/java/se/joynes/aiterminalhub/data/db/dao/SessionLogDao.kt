package se.joynes.aiterminalhub.data.db.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import se.joynes.aiterminalhub.data.db.entity.SessionLogEntity

@Dao
interface SessionLogDao {
    @Query("SELECT * FROM session_logs ORDER BY startedAt DESC")
    fun getAll(): Flow<List<SessionLogEntity>>

    @Query("SELECT * FROM session_logs WHERE serverId = :serverId ORDER BY startedAt DESC")
    fun getByServer(serverId: Long): Flow<List<SessionLogEntity>>

    @Query("SELECT * FROM session_logs WHERE id = :id")
    suspend fun getById(id: Long): SessionLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SessionLogEntity): Long

    @Update
    suspend fun update(entity: SessionLogEntity)
}
