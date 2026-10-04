package se.joynes.terminalhub.data.db.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import se.joynes.terminalhub.data.db.entity.ProjectNoteEntity

@Dao
interface ProjectNoteDao {
    @Query("SELECT * FROM project_notes WHERE projectId = :projectId")
    fun observe(projectId: Long): Flow<ProjectNoteEntity?>
    @Query("SELECT * FROM project_notes WHERE projectId = :projectId")
    suspend fun get(projectId: Long): ProjectNoteEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(note: ProjectNoteEntity)
    @Query("DELETE FROM project_notes WHERE projectId = :projectId")
    suspend fun delete(projectId: Long)
    @Query("DELETE FROM project_notes")
    suspend fun clearAll()
}
