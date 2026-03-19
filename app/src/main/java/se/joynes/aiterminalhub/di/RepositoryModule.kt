package se.joynes.aiterminalhub.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule
// All repositories are @Singleton with @Inject constructors - no explicit bindings needed
