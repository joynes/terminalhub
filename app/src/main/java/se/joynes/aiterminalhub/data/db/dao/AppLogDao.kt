package se.joynes.aiterminalhub.data.db.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import se.joynes.aiterminalhub.data.db.entity.AppLogEntity

@Dao
interface AppLogDao {
    @Query("SELECT * FROM app_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<AppLogEntity>>

    @Query("SELECT * FROM app_logs WHERE level = :level ORDER BY timestamp DESC LIMIT :limit")
    fun getByLevel(level: String, limit: Int): Flow<List<AppLogEntity>>

    @Query("SELECT * FROM app_logs WHERE message LIKE '%' || :query || '%' OR tag LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT :limit")
    fun search(query: String, limit: Int): Flow<List<AppLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: AppLogEntity)

    @Query("DELETE FROM app_logs WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)
}
