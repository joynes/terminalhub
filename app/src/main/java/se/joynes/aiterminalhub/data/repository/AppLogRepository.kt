package se.joynes.aiterminalhub.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import se.joynes.aiterminalhub.data.db.dao.AppLogDao
import se.joynes.aiterminalhub.data.model.AppLogEntry
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLogRepository @Inject constructor(
    private val dao: AppLogDao
) {
    fun recentLogs(limit: Int = 500): Flow<List<AppLogEntry>> =
        dao.getRecent(limit).map { list ->
            list.asReversed().map { AppLogEntry(it.id, it.timestamp, it.level, it.tag, it.message, it.eventType) }
        }

    fun logsByLevel(level: String, limit: Int = 500): Flow<List<AppLogEntry>> =
        dao.getByLevel(level, limit).map { list ->
            list.asReversed().map { AppLogEntry(it.id, it.timestamp, it.level, it.tag, it.message, it.eventType) }
        }

    fun search(query: String, limit: Int = 500): Flow<List<AppLogEntry>> =
        dao.search(query, limit).map { list ->
            list.asReversed().map { AppLogEntry(it.id, it.timestamp, it.level, it.tag, it.message, it.eventType) }
        }
}
