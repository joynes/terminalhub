package se.joynes.aiterminalhub.data.ssh

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.Session
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import se.joynes.aiterminalhub.data.logging.AppLogger
import se.joynes.aiterminalhub.data.logging.LogLevel
import se.joynes.aiterminalhub.data.model.ServerStatus
import javax.inject.Inject

class ServerStatusPoller @Inject constructor(
    private val logger: AppLogger
) {
    private val TAG = "ServerStatusPoller"

    fun pollStatus(session: Session, serverId: Long, intervalMs: Long = 5000L): Flow<ServerStatus> = flow {
        while (true) {
            try {
                emit(fetchStatus(session, serverId))
            } catch (e: Exception) {
                logger.log(LogLevel.WARN, TAG, "Poll failed: ${e.message}")
            }
            delay(intervalMs)
        }
    }.flowOn(Dispatchers.IO)

    private fun fetchStatus(session: Session, serverId: Long): ServerStatus {
        val topOutput = exec(session, "top -bn1 | grep -E 'Cpu|cpu|MiB Mem|KiB Mem' | head -4")
        val dfOutput = exec(session, "df -h / | tail -1")

        var cpuPercent = 0f
        var ramPercent = 0f
        var diskPercent = 0f

        topOutput.lines().forEach { line ->
            when {
                line.contains("Cpu", ignoreCase = true) -> {
                    val idle = Regex("(\\d+\\.?\\d*)\\s*id").find(line)?.groupValues?.get(1)?.toFloatOrNull()
                    if (idle != null) cpuPercent = 100f - idle
                }
                line.contains("Mem", ignoreCase = true) -> {
                    val total = Regex("(\\d+\\.?\\d*)\\s*total").find(line)?.groupValues?.get(1)?.toFloatOrNull()
                    val used = Regex("(\\d+\\.?\\d*)\\s*used").find(line)?.groupValues?.get(1)?.toFloatOrNull()
                    if (total != null && used != null && total > 0) ramPercent = (used / total) * 100f
                }
            }
        }
        val diskMatch = Regex("(\\d+)%").find(dfOutput)
        diskPercent = diskMatch?.groupValues?.get(1)?.toFloatOrNull() ?: 0f

        return ServerStatus(serverId, cpuPercent, ramPercent, diskPercent)
    }

    private fun exec(session: Session, cmd: String): String {
        return try {
            val channel = session.openChannel("exec") as ChannelExec
            channel.setCommand(cmd)
            channel.connect()
            val out = channel.inputStream.bufferedReader().readText()
            channel.disconnect()
            out
        } catch (e: Exception) { "" }
    }
}
