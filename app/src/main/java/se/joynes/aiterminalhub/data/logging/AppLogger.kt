package se.joynes.aiterminalhub.data.logging

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import se.joynes.aiterminalhub.data.db.dao.AppLogDao
import se.joynes.aiterminalhub.data.db.entity.AppLogEntity
import android.util.Log
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLogger @Inject constructor(
    private val appLogDao: AppLogDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _logFlow = MutableSharedFlow<AppLogEntity>(replay = 100, extraBufferCapacity = 200)
    val logFlow: SharedFlow<AppLogEntity> = _logFlow.asSharedFlow()

    fun log(level: LogLevel, tag: String, msg: String, event: LogEvent? = null) {
        val entity = AppLogEntity(
            timestamp = Instant.now().toEpochMilli(),
            level = level.name,
            tag = tag,
            message = msg,
            eventType = event?.javaClass?.simpleName
        )
        // Mirror to Android logcat for debugging (no-op in unit tests)
        try {
            when (level) {
                LogLevel.TRACE -> Log.v(tag, msg)
                LogLevel.DEBUG -> Log.d(tag, msg)
                LogLevel.INFO  -> Log.i(tag, msg)
                LogLevel.WARN  -> Log.w(tag, msg)
                LogLevel.ERROR -> Log.e(tag, msg)
            }
        } catch (_: RuntimeException) { /* Android stub not available in unit tests */ }
        scope.launch {
            _logFlow.emit(entity)
            try { appLogDao.insert(entity) } catch (_: Exception) {}
        }
    }
}
