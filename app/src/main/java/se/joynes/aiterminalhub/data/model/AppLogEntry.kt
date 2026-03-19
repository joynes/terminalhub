package se.joynes.aiterminalhub.data.model

data class AppLogEntry(
    val id: Long = 0,
    val timestamp: Long,
    val level: String,
    val tag: String,
    val message: String,
    val eventType: String? = null
)
