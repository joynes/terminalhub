package se.joynes.terminalhub.data.db.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import se.joynes.terminalhub.data.db.entity.TextInputHistoryEntity

@Dao
interface TextInputHistoryDao {

    @Query("SELECT * FROM text_input_history WHERE projectId = :projectId ORDER BY createdAt DESC, id DESC LIMIT 100")
    fun getRecentForProject(projectId: Long): Flow<List<TextInputHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TextInputHistoryEntity)

    @Query("""
        DELETE FROM text_input_history WHERE id IN (
            SELECT id FROM text_input_history WHERE projectId = :projectId
            ORDER BY createdAt DESC, id DESC LIMIT -1 OFFSET 100
        )
    """)
    suspend fun pruneOldest(projectId: Long)

    @Query("DELETE FROM text_input_history")
    suspend fun clearAll()

    @Query("DELETE FROM text_input_history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT text FROM text_input_history WHERE projectId = :projectId ORDER BY createdAt DESC, id DESC LIMIT 1")
    suspend fun latestText(projectId: Long): String?

    @Transaction
    suspend fun saveRecent(entity: TextInputHistoryEntity) {
        if (latestText(entity.projectId) != entity.text) insert(entity)
        pruneOldest(entity.projectId)
    }
}
