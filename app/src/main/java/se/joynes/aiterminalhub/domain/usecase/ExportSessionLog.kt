package se.joynes.aiterminalhub.domain.usecase

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import se.joynes.aiterminalhub.data.repository.AppLogRepository
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class ExportSessionLog @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appLogRepository: AppLogRepository
) {
    suspend operator fun invoke(): File {
        val logs = appLogRepository.recentLogs(1000).first()
        val file = File(context.getExternalFilesDir(null), "app_log_${System.currentTimeMillis()}.txt")
        file.printWriter().use { writer ->
            logs.forEach { entry ->
                writer.println("[${entry.level}] ${entry.timestamp} [${entry.tag}] ${entry.message}")
            }
        }
        return file
    }
}
