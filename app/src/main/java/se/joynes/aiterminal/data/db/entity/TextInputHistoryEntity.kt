package se.joynes.aiterminal.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "text_input_history")
data class TextInputHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val text: String,
    val createdAt: Long = System.currentTimeMillis()
)
