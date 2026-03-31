package se.joynes.aiterminalhub.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import se.joynes.aiterminalhub.data.db.AppDatabase
import se.joynes.aiterminalhub.data.db.MIGRATION_1_2
import se.joynes.aiterminalhub.data.db.MIGRATION_2_3
import se.joynes.aiterminalhub.data.db.MIGRATION_3_4
import se.joynes.aiterminalhub.data.db.MIGRATION_4_5
import se.joynes.aiterminalhub.data.db.MIGRATION_5_6
import se.joynes.aiterminalhub.data.db.MIGRATION_6_7
import se.joynes.aiterminalhub.data.db.MIGRATION_7_8
import se.joynes.aiterminalhub.data.db.dao.*
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "aiterminalhub.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
            .build()

    @Provides fun provideServerDao(db: AppDatabase): ServerDao = db.serverDao()
    @Provides fun provideProjectDao(db: AppDatabase): ProjectDao = db.projectDao()
    @Provides fun provideSessionLogDao(db: AppDatabase): SessionLogDao = db.sessionLogDao()
    @Provides fun provideAppLogDao(db: AppDatabase): AppLogDao = db.appLogDao()
    @Provides fun provideTextInputHistoryDao(db: AppDatabase): TextInputHistoryDao = db.textInputHistoryDao()
}
