package se.joynes.aiterminalhub.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import se.joynes.aiterminalhub.data.db.AppDatabase
import se.joynes.aiterminalhub.data.db.dao.*
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "aiterminalhub.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideServerDao(db: AppDatabase): ServerDao = db.serverDao()
    @Provides fun provideProjectDao(db: AppDatabase): ProjectDao = db.projectDao()
    @Provides fun provideSessionLogDao(db: AppDatabase): SessionLogDao = db.sessionLogDao()
    @Provides fun provideAppLogDao(db: AppDatabase): AppLogDao = db.appLogDao()
}
