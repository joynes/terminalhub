package se.joynes.terminalhub.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pinned_actions")
data class PinnedActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val text: String,
    val scope: String = "PROJECT",
    val projectId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val usageCount: Long = 0,
    val lastUsedAt: Long? = null,
    val sendEnter: Boolean = true
)
