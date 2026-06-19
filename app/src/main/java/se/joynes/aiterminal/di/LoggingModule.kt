package se.joynes.aiterminal.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object LoggingModule
// AppLogger is @Singleton with @Inject constructor - no explicit bindings needed
