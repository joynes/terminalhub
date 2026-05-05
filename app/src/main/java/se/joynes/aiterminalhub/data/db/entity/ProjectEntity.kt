package se.joynes.aiterminalhub.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: Long,
    val targetType: String = "ssh",
    val name: String,
    val useTmux: Boolean = true,
    val customScript: String = "cd {{PROJECT_PATH}}",
    val aiCommand: String = "",
    val colorSeed: Int = (Math.random() * Int.MAX_VALUE).toInt(),
    val createdAt: Long = System.currentTimeMillis(),
    val gitUrl: String = "",
    val lastOpenedAt: Long = 0L
)
