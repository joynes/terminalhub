package se.joynes.aiterminalhub.data.db.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import se.joynes.aiterminalhub.data.db.entity.TextInputHistoryEntity

@Dao
interface TextInputHistoryDao {

    @Query("SELECT * FROM text_input_history WHERE projectId = :projectId ORDER BY createdAt DESC LIMIT 10")
    fun getRecentForProject(projectId: Long): Flow<List<TextInputHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TextInputHistoryEntity)

    @Query("""
        DELETE FROM text_input_history WHERE id IN (
            SELECT id FROM text_input_history WHERE projectId = :projectId
            ORDER BY createdAt DESC LIMIT -1 OFFSET 10
        )
    """)
    suspend fun pruneOldest(projectId: Long)
}
