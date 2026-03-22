package se.joynes.aiterminalhub.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: Long,
    val name: String,
    // null = use server's default setupScript; empty string = run nothing on connect
    val setupScript: String? = null,
    val colorSeed: Int = (Math.random() * Int.MAX_VALUE).toInt(),
    val createdAt: Long = System.currentTimeMillis()
)
