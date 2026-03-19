package se.joynes.aiterminalhub.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_logs")
data class AppLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val level: String,
    val tag: String,
    val message: String,
    val eventType: String? = null
)
