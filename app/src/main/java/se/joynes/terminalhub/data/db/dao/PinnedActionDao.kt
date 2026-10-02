package se.joynes.terminalhub.data.db.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import se.joynes.terminalhub.data.db.entity.PinnedActionEntity

@Dao
interface PinnedActionDao {
    @Query("SELECT * FROM pinned_actions WHERE scope = 'GLOBAL' OR (scope = 'PROJECT' AND projectId = :projectId) ORDER BY scope DESC, name COLLATE NOCASE, id")
    fun forProject(projectId: Long): Flow<List<PinnedActionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(action: PinnedActionEntity): Long

    @Query("DELETE FROM pinned_actions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE pinned_actions SET usageCount = usageCount + 1, lastUsedAt = :now WHERE id = :id")
    suspend fun markUsed(id: Long, now: Long)

    @Query("SELECT * FROM pinned_actions ORDER BY id")
    suspend fun all(): List<PinnedActionEntity>

    @Query("DELETE FROM pinned_actions")
    suspend fun clearAll()
}
