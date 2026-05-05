package se.joynes.aiterminalhub.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import se.joynes.aiterminalhub.data.db.dao.*
import se.joynes.aiterminalhub.data.db.entity.*

@Database(
    entities = [
        ServerEntity::class,
        ProjectEntity::class,
        SessionLogEntity::class,
        AppLogEntity::class,
        TextInputHistoryEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun projectDao(): ProjectDao
    abstract fun sessionLogDao(): SessionLogDao
    abstract fun appLogDao(): AppLogDao
    abstract fun textInputHistoryDao(): TextInputHistoryDao
}
