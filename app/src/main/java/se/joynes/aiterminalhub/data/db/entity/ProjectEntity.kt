package se.joynes.aiterminalhub.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: Long,
    val name: String,
    val projectPath: String,
    val sessionName: String,
    val setupScript: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
