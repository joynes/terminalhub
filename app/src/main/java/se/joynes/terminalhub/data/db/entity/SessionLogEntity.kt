package se.joynes.terminalhub.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "session_logs")
data class SessionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val serverId: Long,
    val projectId: Long? = null,
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
    val outputPath: String? = null
)
