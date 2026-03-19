package se.joynes.aiterminalhub.data.model

data class ServerStatus(
    val serverId: Long,
    val cpuPercent: Float = 0f,
    val ramPercent: Float = 0f,
    val diskPercent: Float = 0f,
    val uptimeSeconds: Long = 0L,
    val loadAvg1: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
)
