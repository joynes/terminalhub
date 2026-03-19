package se.joynes.aiterminalhub.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "servers")
data class ServerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val host: String,
    val port: Int = 22,
    val username: String,
    val authType: String = "password", // "password" or "key"
    val keyAlias: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
