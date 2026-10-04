package se.joynes.terminalhub.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "project_notes")
data class ProjectNoteEntity(
    @PrimaryKey val projectId: Long,
    val text: String = "",
    val localUpdatedAt: Long = 0,
    val dirty: Boolean = false,
    val remoteKey: String? = null,
    val lastSyncedRemoteMtime: Long? = null,
    val lastSyncedContentHash: String? = null,
    val syncError: String? = null,
    val pendingDelete: Boolean = false
)
